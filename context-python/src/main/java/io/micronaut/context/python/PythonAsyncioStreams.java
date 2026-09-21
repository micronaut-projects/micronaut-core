/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Java half of Python-native streaming: a Reactive Streams {@link Publisher} consumed by a
 * Python {@code async for}, and a Python async iterator exposed as a {@link Publisher}.
 * <p>
 * Both directions keep the reactive signals and the Python state on different sides of one rule:
 * Python runs only on the asyncio loop that owns the stream. Reactive signals arriving on
 * arbitrary threads are queued onto the Micronaut event loop, inside an execution frame of the
 * stream's context, before any Python callback runs. Without a Micronaut loop (a plain asyncio
 * loop in a test), the callbacks run on the signalling thread and the Python side hops onto its
 * loop with {@code call_soon_threadsafe}. The Python state machines live in the
 * {@code micronaut_asyncio} module ({@code as_async_iterable} and {@code as_publisher}).
 *
 * @since 5.2.3
 */
@Internal
@Experimental
public final class PythonAsyncioStreams {
    private static final Logger LOG = LoggerFactory.getLogger(PythonAsyncioStreams.class);
    private static final long SATURATED = Long.MAX_VALUE;

    private PythonAsyncioStreams() {
    }

    /**
     * Java entry point of {@code as_async_iterable}: a demand-driven subscriber over a publisher
     * that reports its signals to Python callbacks.
     *
     * @param publisher The publisher, or any value convertible to one
     * @param eventLoop The Micronaut event loop of the consuming coroutine, or {@code null}
     * @param callbacks A Python object with {@code on_next}, {@code on_error} and {@code on_complete}
     * @param reactiveContext The reactive context of the consuming coroutine to subscribe within, or {@code null}
     * @return The subscriber; Python calls {@link PublisherIterator#request()} and {@link PublisherIterator#cancel()}
     */
    public static PublisherIterator iterator(Value publisher, @Nullable PythonEventLoop eventLoop, Value callbacks, @Nullable PythonReactiveContext reactiveContext) {
        Objects.requireNonNull(callbacks, "callbacks");
        Object source = publisher.isHostObject() ? publisher.asHostObject() : publisher;
        if (!Publishers.isConvertibleToPublisher(source)) {
            throw new IllegalArgumentException("as_async_iterable expects a Java Publisher, got [" + describe(publisher) + "]");
        }
        return new PublisherIterator(Publishers.convertToPublisher(ConversionService.SHARED, source), eventLoop, callbacks, reactiveContext);
    }

    /**
     * Java entry point of {@code as_publisher}: a cold publisher whose every subscription starts a
     * Python driver over a fresh async iterator.
     *
     * @param start A Python callable taking the {@link GeneratorSubscription} and returning the driver, a
     * Python object with {@code request(n)} and {@code cancel()}
     * @param eventLoop The Micronaut event loop the driver runs on, or {@code null} for a plain asyncio loop
     * @return The publisher
     */
    public static Publisher<Object> publisher(Value start, @Nullable PythonEventLoop eventLoop) {
        Objects.requireNonNull(start, "start");
        return new GeneratorPublisher(start, eventLoop);
    }

    private static String describe(Value value) {
        Value metaObject = value.getMetaObject();
        return metaObject == null ? value.toString() : metaObject.getMetaQualifiedName();
    }

    /**
     * Run Python work on the loop, inside an execution frame of the context.
     *
     * @param eventLoop The loop, or null to run on the calling thread
     * @param context The context of the Python work
     * @param work The work
     * @param refused Runs when the work could not be scheduled or the context is closing
     */
    private static void dispatch(@Nullable PythonEventLoop eventLoop, Context context, Runnable work, Runnable refused) {
        if (eventLoop == null) {
            if (!PythonContextRegistry.tryWithExecutionFrame(context, work)) {
                refused.run();
            }
            return;
        }
        try {
            eventLoop.execute(() -> {
                if (!PythonContextRegistry.tryWithExecutionFrame(context, work)) {
                    refused.run();
                }
            });
        } catch (RuntimeException e) {
            LOG.debug("The event loop refused a stream signal", e);
            refused.run();
        }
    }

    /**
     * The Java view of an element yielded by Python: primitives, strings and host objects unwrap,
     * every other Python object stays a {@link Value} for the generated bridge to convert with the
     * declared element type.
     */
    private static @Nullable Object hostView(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.as(Object.class);
        }
        return value;
    }

    /**
     * Subscribes to a publisher one item at a time on behalf of a Python async iterator.
     * <p>
     * The subscription is lazy: the first {@link #request()} subscribes. Every request asks for one
     * item; requests made before {@code onSubscribe} arrives are accumulated. Signals reach the
     * Python callbacks through {@link #dispatch}, and once the context refuses the frame (it is
     * closing) the subscription is cancelled so no further signals are attempted.
     */
    @Internal
    public static final class PublisherIterator implements Subscriber<Object> {
        private final Publisher<Object> publisher;
        private final @Nullable PythonEventLoop eventLoop;
        private final Value callbacks;
        private final Context context;
        private final @Nullable PythonReactiveContext reactiveContext;
        private final AtomicReference<@Nullable Subscription> subscription = new AtomicReference<>();
        private final AtomicLong pendingRequests = new AtomicLong();
        /** Items requested and not yet delivered: an item arriving while this is zero has no demand. */
        private final AtomicLong outstandingDemand = new AtomicLong();
        private final AtomicBoolean subscribed = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminated = new AtomicBoolean();

        private PublisherIterator(Publisher<Object> publisher, @Nullable PythonEventLoop eventLoop, Value callbacks, @Nullable PythonReactiveContext reactiveContext) {
            this.reactiveContext = reactiveContext;
            this.publisher = publisher;
            this.eventLoop = eventLoop;
            this.callbacks = callbacks;
            this.context = callbacks.getContext();
        }

        /**
         * Request one more item, subscribing first when this is the first request.
         */
        public void request() {
            if (cancelled.get() || terminated.get()) {
                return;
            }
            outstandingDemand.incrementAndGet();
            pendingRequests.incrementAndGet();
            if (subscribed.compareAndSet(false, true)) {
                try {
                    // within the coroutine's reactive context, as an awaited publisher is
                    PythonPublishers.subscribe(publisher, this, reactiveContext);
                } catch (RuntimeException e) {
                    onError(e);
                }
                return;
            }
            requestPending();
        }

        /**
         * Cancel the subscription; later signals are ignored. Idempotent.
         */
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            Subscription current = subscription.get();
            if (current != null) {
                current.cancel();
            }
        }

        /**
         * @return Whether the subscription was cancelled from the Python side
         */
        public boolean isCancelled() {
            return cancelled.get();
        }

        private void requestPending() {
            Subscription current = subscription.get();
            if (current == null) {
                return;
            }
            long requested = pendingRequests.getAndSet(0);
            if (requested > 0) {
                current.request(requested);
            }
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription");
            if (!this.subscription.compareAndSet(null, subscription)) {
                subscription.cancel();
                return;
            }
            if (cancelled.get()) {
                subscription.cancel();
                return;
            }
            requestPending();
        }

        @Override
        public void onNext(Object item) {
            Objects.requireNonNull(item, "item");
            if (cancelled.get() || terminated.get()) {
                return;
            }
            if (outstandingDemand.decrementAndGet() < 0) {
                // Reactive Streams 1.1: an item for which nothing asked. The iteration fails here rather
                // than buffering the item, so one item per pending __anext__ stays the whole story and
                // memory cannot grow with a publisher that ignores demand.
                onError(new IllegalStateException("The publisher emitted an item without demand; as_async_iterable requests one item per iteration"));
                cancel();
                return;
            }
            dispatch(eventLoop, context, () -> callbacks.invokeMember("on_next", item), this::cancel);
        }

        @Override
        public void onError(Throwable throwable) {
            Objects.requireNonNull(throwable, "throwable");
            if (cancelled.get() || !terminated.compareAndSet(false, true)) {
                return;
            }
            dispatch(eventLoop, context, () -> callbacks.invokeMember("on_error", throwable), () -> LOG.debug("Dropping a publisher error whose Python context is closing", throwable));
        }

        @Override
        public void onComplete() {
            if (cancelled.get() || !terminated.compareAndSet(false, true)) {
                return;
            }
            dispatch(eventLoop, context, () -> callbacks.invokeMember("on_complete"), () -> LOG.debug("Dropping a publisher completion whose Python context is closing"));
        }
    }

    /**
     * A cold publisher over a Python async iterator factory: each subscriber gets a driver of its
     * own, created on the loop.
     */
    private static final class GeneratorPublisher implements Publisher<Object> {
        private final Value start;
        private final @Nullable PythonEventLoop eventLoop;
        private final Context context;

        private GeneratorPublisher(Value start, @Nullable PythonEventLoop eventLoop) {
            this.start = start;
            this.eventLoop = eventLoop;
            this.context = start.getContext();
        }

        @Override
        public void subscribe(Subscriber<? super Object> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber");
            GeneratorSubscription subscription = new GeneratorSubscription(subscriber, start, eventLoop, context);
            subscriber.onSubscribe(subscription);
            subscription.start();
        }
    }

    /**
     * The subscription handed to a Java subscriber of a Python async iterator, and the facade the
     * Python driver signals through.
     * <p>
     * Demand and cancellation travel to the driver on the loop; the driver advances the iterator
     * only with positive demand and reports elements, the error or the completion back through
     * {@link #next}, {@link #error} and {@link #complete}. A terminal signal is emitted at most
     * once, and no element after it. The stream counts as an active execution of its context from
     * the subscription until the driver has released the iterator, so a graceful shutdown waits
     * for the generator's cleanup.
     */
    @Internal
    public static final class GeneratorSubscription implements Subscription {
        private final Subscriber<? super Object> subscriber;
        private final Value start;
        private final @Nullable PythonEventLoop eventLoop;
        private final Context context;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean executing = new AtomicBoolean();
        // driver and pendingDemand are only touched on the loop (or, without a loop, under the GIL)
        private @Nullable Value driver;
        private long pendingDemand;
        private boolean startFailed;

        private GeneratorSubscription(Subscriber<? super Object> subscriber, Value start, @Nullable PythonEventLoop eventLoop, Context context) {
            this.subscriber = subscriber;
            this.start = start;
            this.eventLoop = eventLoop;
            this.context = context;
        }

        private void start() {
            try {
                PythonContextRegistry.enterExecution(context);
            } catch (RuntimeException e) {
                fail(e);
                return;
            }
            executing.set(true);
            dispatch(eventLoop, context, this::createDriver, () -> failWithoutPython(new IllegalStateException("The Python context of the stream is closing")));
        }

        private void createDriver() {
            Value created;
            try {
                created = start.execute(this);
            } catch (RuntimeException e) {
                // no driver: no Python code of this stream will run, so the lease ends here
                startFailed = true;
                failWithoutPython(e);
                return;
            }
            driver = created;
            if (cancelled.get()) {
                created.invokeMember("cancel");
                return;
            }
            long demand = pendingDemand;
            pendingDemand = 0;
            if (demand > 0) {
                created.invokeMember("request", demand);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                // Reactive Streams 3.9: signal the violation, once, then stop the driver
                fail(new IllegalArgumentException("Reactive Streams 3.9 violation: request(" + n + ") must be positive"));
                if (cancelled.compareAndSet(false, true)) {
                    dispatch(eventLoop, context, this::cancelDriver, this::release);
                }
                return;
            }
            if (cancelled.get() || terminated.get()) {
                return;
            }
            dispatch(eventLoop, context, () -> {
                if (startFailed || terminated.get()) {
                    return;
                }
                if (driver == null) {
                    pendingDemand = pendingDemand + n < 0 ? SATURATED : pendingDemand + n;
                } else {
                    driver.invokeMember("request", n);
                }
            }, () -> failWithoutPython(new IllegalStateException("The Python context of the stream is closing")));
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            dispatch(eventLoop, context, this::cancelDriver, () -> {
                // the context is closing: its own teardown disposes the generator, since no Python of
                // this stream can run any more, and the lease must not outlive that
                LOG.debug("Cancelling a Python stream whose context is closing: the generator is not closed by this stream");
                release();
            });
        }

        private void cancelDriver() {
            Value current = driver;
            if (current != null) {
                current.invokeMember("cancel");
            } else if (startFailed) {
                release();
            }
            // a driver that has not been created yet sees cancelled in createDriver
        }

        /**
         * Python entry point: an element the iterator produced.
         *
         * @param item The element, never None
         */
        public void next(Value item) {
            if (terminated.get() || cancelled.get()) {
                return;
            }
            Object element = hostView(item);
            if (element == null) {
                fail(new NullPointerException("The Python async iterator yielded None; Reactive Streams does not allow null elements"));
                return;
            }
            try {
                subscriber.onNext(element);
            } catch (RuntimeException e) {
                // Reactive Streams 2.13: the subscriber must not throw; treat it as a cancellation
                LOG.debug("The subscriber threw from onNext; cancelling the Python stream", e);
                terminated.set(true);
                cancel();
            }
        }

        /**
         * Python entry point: the iterator raised.
         *
         * @param exception The Python exception, or a Java throwable
         */
        public void error(Value exception) {
            Throwable throwable;
            try {
                throwable = GraalPyExceptionHandler.toHostThrowable(exception);
            } catch (RuntimeException e) {
                throwable = e;
            }
            fail(throwable);
        }

        /**
         * Python entry point: the iterator is exhausted.
         */
        public void complete() {
            if (cancelled.get() || !terminated.compareAndSet(false, true)) {
                return;
            }
            subscriber.onComplete();
        }

        /**
         * Python entry point: the iterator has been closed, nothing of the stream runs any more.
         */
        public void released() {
            release();
        }

        /**
         * @return Whether the subscriber cancelled or a terminal signal was emitted
         */
        public boolean isDone() {
            return cancelled.get() || terminated.get();
        }

        /**
         * Fail the subscriber. The lease stays held: the driver that reported this failure closes its
         * iterator and reports {@link #released()} once that has happened.
         */
        private void fail(Throwable throwable) {
            boolean emit = terminated.compareAndSet(false, true) && !cancelled.get();
            if (emit) {
                subscriber.onError(throwable);
            } else {
                LOG.debug("Dropping a Python stream failure after a terminal signal or cancellation", throwable);
            }
        }

        /**
         * Fail the subscriber when no Python of this stream can run any more: the context is closing, or
         * the driver was never created. Nothing will report {@link #released()}, so the lease ends here;
         * a generator left open is disposed by the close of its own context.
         */
        private void failWithoutPython(Throwable throwable) {
            fail(throwable);
            release();
        }

        private void release() {
            if (executing.get() && released.compareAndSet(false, true)) {
                PythonContextRegistry.exitExecution(context);
            }
        }
    }
}
