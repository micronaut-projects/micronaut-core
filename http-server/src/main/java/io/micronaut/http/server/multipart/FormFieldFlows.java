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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
     *
     * @param source The publisher
     * @param map    Maps the item, must not return {@code null}
     * @param <T>    The type of the item
     * @param <R>    The type of the result
     * @return The result
     */
    public static <T, R> CompletableFuture<@Nullable R> first(Publisher<T> source, Function<? super T, ? extends R> map) {
        CompletableFuture<@Nullable R> result = new CompletableFuture<>();
        source.subscribe(new FirstSubscriber<T>(result) {
            @Override
            void onFirst(T item) {
                R mapped;
                try {
                    mapped = Objects.requireNonNull(map.apply(item), "The mapper returned a null value.");
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                    return;
                }
                result.complete(mapped);
            }
        });
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
     * is then not mapped.
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
        CompletableFuture<@Nullable R> result = new CompletableFuture<>();
        source.subscribe(new FirstSubscriber<T>(result) {
            @Override
            void onFirst(T item) {
                ExecutionFlow<? extends V> flow;
                try {
                    flow = Objects.requireNonNull(complete.apply(item), "The mapper returned a null flow");
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                    return;
                }
                flow.onComplete((value, error) -> {
                    if (error != null) {
                        result.completeExceptionally(error);
                    } else if (value == null) {
                        result.complete(null);
                    } else {
                        R mapped;
                        try {
                            mapped = Objects.requireNonNull(map.apply(value), "The mapper returned a null value.");
                        } catch (Throwable e) {
                            result.completeExceptionally(e);
                            return;
                        }
                        result.complete(mapped);
                    }
                });
            }
        });
        return result;
    }

    /**
     * Takes the first item of a publisher: requests every item, and cancels the publisher once
     * the first one arrived.
     *
     * @param <T> The type of the item
     */
    private abstract static class FirstSubscriber<T> implements Subscriber<T> {
        private final CompletableFuture<?> result;
        @Nullable
        private Subscription subscription;
        private boolean done;

        FirstSubscriber(CompletableFuture<?> result) {
            this.result = result;
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
            s.request(Long.MAX_VALUE);
        }

        @Override
        public final void onNext(T item) {
            if (done) {
                return;
            }
            done = true;
            Objects.requireNonNull(subscription).cancel();
            onFirst(item);
        }

        @Override
        public final void onError(Throwable t) {
            if (done) {
                return;
            }
            done = true;
            result.completeExceptionally(t);
        }

        @Override
        public final void onComplete() {
            if (done) {
                return;
            }
            done = true;
            result.complete(null);
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
    public static final class Concat<T, R> implements Subscriber<T> {
        private final AtomicReference<State> state = new AtomicReference<>(State.INITIAL);
        private final AtomicReference<@Nullable Throwable> error = new AtomicReference<>();
        private final Function<? super T, ? extends ExecutionFlow<? extends R>> complete;
        private final Consumer<? super T> discard;
        private final Consumer<? super R> onValue;
        private final Consumer<@Nullable Throwable> onDone;
        @Nullable
        private volatile Subscription upstream;
        @Nullable
        private volatile ExecutionFlow<? extends R> running;

        /**
         * @param complete Completes an item
         * @param discard  Releases an item that is not completed
         * @param onValue  Receives the value of a flow, in order, until the reading stops; may
         *                 {@link #cancel()} the reading
         * @param onDone   Called once when every item was completed, with {@code null}, or the
         *                 reading failed, with the error; not called when the reading was
         *                 cancelled
         */
        public Concat(Function<? super T, ? extends ExecutionFlow<? extends R>> complete,
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
            if (flow.tryComplete() == null) {
                // cancelled with the reading while it runs
                running = flow;
            }
            flow.onComplete((value, e) -> {
                if (e != null) {
                    stopWithError(e, Objects.requireNonNull(upstream)::cancel);
                } else {
                    if (value != null) {
                        innerNext(value);
                    }
                    innerComplete();
                }
            });
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
        public void cancel() {
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
            ExecutionFlow<? extends R> flow = running;
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
