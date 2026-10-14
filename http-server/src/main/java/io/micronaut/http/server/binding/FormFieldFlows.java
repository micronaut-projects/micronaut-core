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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.stream.ReactorInterop;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads the fields of a form, e.g. those of a {@link FormRouteCompleter} subscription, without
 * a reactive library: the first field of a publisher, or every field in order, one at a time.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class FormFieldFlows {

    private FormFieldFlows() {
    }

    /**
     * Take the first item of a publisher and map it. The publisher is cancelled once the item
     * arrived; the result is {@code null} when the publisher completes without an item.
     * Cancelling the result cancels the publisher, like the future of a {@code Mono}.
     *
     * @param source The publisher
     * @param map    Maps the item, must not return {@code null}
     * @param <T>    The type of the item
     * @param <R>    The type of the result
     * @return The result
     */
    public static <T, R> CompletableFuture<@Nullable R> first(Publisher<T> source, Function<? super T, ? extends R> map) {
        FirstFuture<T, @Nullable R> result = new FirstFuture<>() {
            @Override
            void onFirst(T item) {
                R mapped;
                try {
                    mapped = Objects.requireNonNull(map.apply(item), "The mapper returned a null value.");
                } catch (Throwable e) {
                    // nobody took the item
                    close(item, e);
                    completeExceptionally(e);
                    return;
                }
                complete(mapped);
            }
        };
        ReactorInterop.subscribe(source, result, null, null);
        return result;
    }

    /**
     * Take the first item of a publisher and complete it with a flow. The publisher is cancelled
     * once the item arrived; the result is {@code null} when the publisher completes without an
     * item, or the flow completes without a value.
     *
     * @param source   The publisher
     * @param complete Completes the item
     * @param <T>      The type of the item
     * @param <R>      The type of the result
     * @return The result
     */
    public static <T, R> CompletableFuture<@Nullable R> firstFlatMap(Publisher<T> source,
                                                                    Function<? super T, ? extends ExecutionFlow<? extends R>> complete) {
        return firstFlatMap(source, complete, Function.identity());
    }

    /**
     * Take the first item of a publisher, complete it with a flow, and map the value of the flow.
     * The publisher is cancelled once the item arrived; the result is {@code null} when the
     * publisher completes without an item, or the flow completes without a value, and the value
     * is then not mapped. Cancelling the result cancels the publisher, or the flow once the item
     * arrived, like the future of a {@code Mono}.
     *
     * @param source   The publisher
     * @param complete Completes the item
     * @param map      Maps the value of the flow, must not return {@code null}
     * @param <T>      The type of the item
     * @param <V>      The type of the value of the flow
     * @param <R>      The type of the result
     * @return The result
     */
    public static <T, V, R> CompletableFuture<@Nullable R> firstFlatMap(Publisher<T> source,
                                                                       Function<? super T, ? extends ExecutionFlow<? extends V>> complete,
                                                                       Function<? super V, ? extends R> map) {
        FirstFuture<T, @Nullable R> result = new FirstFuture<>() {
            @Override
            void onFirst(T item) {
                ExecutionFlow<? extends V> flow;
                try {
                    flow = Objects.requireNonNull(complete.apply(item), "The mapper returned a null flow");
                } catch (Throwable e) {
                    // nobody took the item
                    close(item, e);
                    completeExceptionally(e);
                    return;
                }
                var immediate = flow.tryComplete();
                if (immediate != null) {
                    finish(immediate.getValue(), immediate.getError());
                    return;
                }
                flow.onComplete(this::finish);
                // Observe first: a delayed flow refuses observation after cancellation.
                running(flow);
            }

            private void finish(@Nullable V value, @Nullable Throwable error) {
                if (error != null) {
                    completeExceptionally(error);
                } else if (value == null) {
                    complete(null);
                } else {
                    R mapped;
                    try {
                        mapped = Objects.requireNonNull(map.apply(value), "The mapper returned a null value.");
                    } catch (Throwable e) {
                        completeExceptionally(e);
                        return;
                    }
                    complete(mapped);
                }
            }
        };
        ReactorInterop.subscribe(source, result, null, null);
        return result;
    }

    /**
     * Close an item that a mapper failed to take, e.g. a {@link io.micronaut.http.multipart.RawFormField},
     * if it holds resources.
     *
     * @param item    The item
     * @param failure The failure of the mapper, which gets the failure to close as suppressed
     */
    private static void close(Object item, Throwable failure) {
        if (item instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                failure.addSuppressed(e);
            }
        }
    }

    /**
     * Takes the first item of a publisher: requests every item, and cancels the publisher once
     * the first one arrived.
     *
     * @param <T> The type of the item
     * @param <R> The result type
     */
    @Internal
    private abstract static class FirstFuture<T, R extends @Nullable Object> extends CompletableFuture<R> implements Subscriber<T> {
        private final AtomicBoolean done = new AtomicBoolean();
        // References are published, not mutated as containers; done arbitrates item ownership.
        @SuppressWarnings("java:S3077")
        private volatile @Nullable Subscription subscription;
        @SuppressWarnings("java:S3077")
        private volatile @Nullable ExecutionFlow<?> running;
        private volatile boolean cancelled;

        /**
         * The result was cancelled: the publisher, or the flow of the item, is cancelled. An item
         * that arrives afterwards is closed.
         */
        @Override
        public final boolean cancel(boolean mayInterruptIfRunning) {
            if (!super.cancel(mayInterruptIfRunning)) {
                return false;
            }
            cancelled = true;
            done.set(true);
            Subscription s = subscription;
            if (s != null) {
                s.cancel();
            }
            ExecutionFlow<?> flow = running;
            if (flow != null) {
                flow.cancel();
            }
            return true;
        }

        /**
         * @param flow The flow of the item, cancelled with the result
         */
        final void running(ExecutionFlow<?> flow) {
            running = flow;
            if (cancelled) {
                flow.cancel();
            }
        }

        /**
         * Called with the first item, after the publisher was cancelled.
         *
         * @param item The item
         */
        abstract void onFirst(T item);

        @Override
        public final void onSubscribe(Subscription s) {
            subscription = s;
            if (cancelled) {
                s.cancel();
                return;
            }
            s.request(Long.MAX_VALUE);
        }

        @Override
        public final void onNext(T item) {
            if (!done.compareAndSet(false, true)) {
                if (cancelled) {
                    // nobody takes it
                    close(item, new CancellationException("The result was cancelled"));
                }
                return;
            }
            Objects.requireNonNull(subscription).cancel();
            onFirst(item);
        }

        @Override
        public final void onError(Throwable t) {
            if (done.compareAndSet(false, true)) {
                completeExceptionally(t);
            }
        }

        @Override
        public final void onComplete() {
            if (done.compareAndSet(false, true)) {
                complete(null);
            }
        }
    }

    /**
     * Completes every item of a publisher in order, one at a time: the next item is requested
     * once the flow of the previous one completed. An error of the publisher cancels the flow
     * that is running, and an error of a flow cancels the publisher. The items that arrive after
     * the reading stopped are discarded.
     *
     * @param <T> The type of the item
     * @param <R> The type of the value of a flow
     */
    static final class Concat<T, R> implements Subscriber<T> {
        private final AtomicReference<State> state = new AtomicReference<>(State.INITIAL);
        private final AtomicReference<@Nullable Throwable> error = new AtomicReference<>();
        private final Function<? super T, ? extends ExecutionFlow<? extends R>> complete;
        private final Consumer<? super T> discard;
        private final Consumer<? super R> onValue;
        private final Consumer<@Nullable Throwable> onDone;
        // Publication only: compound atomic transitions are confined to state and error.
        @SuppressWarnings("java:S3077") // Publication only; Subscription provides its own concurrency contract.
        private volatile @Nullable Subscription upstream;
        @SuppressWarnings("java:S3077") // Publication only; RunningFlow has atomic cancellation/observation state.
        private volatile @Nullable RunningFlow running;

        /**
         * @param complete Completes an item
         * @param discard  Releases an item that is not completed
         * @param onValue  Receives the value of a flow, in order, until the reading stops; may
         *                 {@link #cancel()} the reading
         * @param onDone   Called once when every item was completed, with {@code null}, or the
         *                 reading failed, with the error; not called when the reading was
         *                 cancelled
         */
        Concat(Function<? super T, ? extends ExecutionFlow<? extends R>> complete,
                      Consumer<? super T> discard,
                      Consumer<? super R> onValue,
                      Consumer<@Nullable Throwable> onDone) {
            this.complete = complete;
            this.discard = discard;
            this.onValue = onValue;
            this.onDone = onDone;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (upstream != null) {
                s.cancel();
                return;
            }
            upstream = s;
            if (state.compareAndSet(State.INITIAL, State.REQUESTED)) {
                s.request(1);
            } else if (state.get() == State.CANCELLED) {
                // cancelled before the subscription arrived: the cancellation did not reach it
                s.cancel();
            }
        }

        @Override
        public void onNext(T item) {
            if (!state.compareAndSet(State.REQUESTED, State.ACTIVE)) {
                // the reading stopped
                discard.accept(item);
                return;
            }
            ExecutionFlow<? extends R> flow;
            try {
                flow = Objects.requireNonNull(complete.apply(item), "The mapper returned a null flow");
            } catch (Throwable e) {
                discard.accept(item);
                stopWithError(e, Objects.requireNonNull(upstream)::cancel);
                return;
            }
            var immediate = flow.tryComplete();
            if (immediate != null) {
                innerFinished(immediate.getValue(), immediate.getError());
                return;
            }
            // Publish before observing completion, which may request another item reentrantly.
            RunningFlow runningFlow = new RunningFlow(flow);
            running = runningFlow;
            flow.onComplete(this::innerFinished);
            State current = state.get();
            if (!runningFlow.observed() || current == State.CANCELLED || current == State.TERMINATED) {
                flow.cancel();
            }
        }

        private void innerFinished(@Nullable R value, @Nullable Throwable failure) {
            if (failure != null) {
                stopWithError(failure, Objects.requireNonNull(upstream)::cancel);
            } else {
                if (value != null) {
                    innerNext(value);
                }
                innerComplete();
            }
        }

        @Override
        public void onError(Throwable t) {
            stopWithError(t, this::cancelRunning);
        }

        @Override
        public void onComplete() {
            while (true) {
                State previous = Objects.requireNonNull(state.get());
                switch (previous) {
                    case INITIAL, REQUESTED -> {
                        if (state.compareAndSet(previous, State.TERMINATED)) {
                            onDone.accept(error.get());
                            return;
                        }
                    }
                    case ACTIVE -> {
                        if (state.compareAndSet(previous, State.LAST_ACTIVE)) {
                            return;
                        }
                    }
                    default -> {
                        return;
                    }
                }
            }
        }

        /**
         * Stop reading: cancel the publisher and the flow that is running. Neither callback is
         * called afterwards, and the items that arrive afterwards are discarded.
         */
        void cancel() {
            switch (Objects.requireNonNull(state.getAndSet(State.CANCELLED))) {
                case CANCELLED -> {
                }
                case TERMINATED -> cancelRunning();
                default -> {
                    cancelRunning();
                    Subscription s = upstream;
                    if (s != null) {
                        s.cancel();
                    }
                }
            }
        }

        private void cancelRunning() {
            RunningFlow flow = running;
            if (flow != null) {
                flow.cancel();
            }
        }

        private synchronized void innerNext(R value) {
            State s = state.get();
            if (s == State.ACTIVE || s == State.LAST_ACTIVE) {
                onValue.accept(value);
            }
        }

        private void innerComplete() {
            running = null;
            while (true) {
                State previous = Objects.requireNonNull(state.get());
                switch (previous) {
                    case ACTIVE -> {
                        if (state.compareAndSet(previous, State.REQUESTED)) {
                            Objects.requireNonNull(upstream).request(1);
                            return;
                        }
                    }
                    case LAST_ACTIVE -> {
                        if (state.compareAndSet(previous, State.TERMINATED)) {
                            onDone.accept(error.get());
                            return;
                        }
                    }
                    default -> {
                        return;
                    }
                }
            }
        }

        /**
         * Stop reading with an error, unless the reading stopped already.
         *
         * @param e        The error
         * @param toCancel Cancels the other side: the publisher or the running flow
         */
        private void stopWithError(Throwable e, Runnable toCancel) {
            error.compareAndSet(null, e);
            while (true) {
                State previous = Objects.requireNonNull(state.get());
                if (previous == State.CANCELLED || previous == State.TERMINATED) {
                    return;
                }
                if (state.compareAndSet(previous, State.TERMINATED)) {
                    toCancel.run();
                    synchronized (this) {
                        onDone.accept(error.get());
                    }
                    return;
                }
            }
        }

        /**
         * The flow of an item that is being completed. A cancellation before its completion is
         * observed is left to {@link #onNext}: a delayed flow refuses to be observed once it was
         * cancelled.
         */
        private static final class RunningFlow {
            private static final int NEW = 0;
            private static final int OBSERVED = 1;
            private static final int CANCELLED = 2;

            private final ExecutionFlow<?> flow;
            private final AtomicInteger state = new AtomicInteger(NEW);

            RunningFlow(ExecutionFlow<?> flow) {
                this.flow = flow;
            }

            /**
             * @return Whether the flow is observed, and not cancelled before: else
             * {@link #onNext} cancels it
             */
            boolean observed() {
                return state.compareAndSet(NEW, OBSERVED);
            }

            void cancel() {
                if (!state.compareAndSet(NEW, CANCELLED)) {
                    flow.cancel();
                }
            }
        }

        private enum State {
            /**
             * Not subscribed yet.
             */
            INITIAL,
            /**
             * An item was requested from the publisher.
             */
            REQUESTED,
            /**
             * An item is being completed.
             */
            ACTIVE,
            /**
             * The publisher completed, the last item is being completed.
             */
            LAST_ACTIVE,
            /**
             * Completed, or failed.
             */
            TERMINATED,
            /**
             * Cancelled.
             */
            CANCELLED,
        }
    }
}
