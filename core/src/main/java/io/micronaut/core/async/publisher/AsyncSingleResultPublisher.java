/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.async.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * A {@link org.reactivestreams.Publisher} that uses an {@link ExecutorService} to emit a single result.
 * @param <T> The argument type
 * @author Graeme Rocher
 * @since 1.0
 */
public class AsyncSingleResultPublisher<T> implements Publisher<T> {
    private final ExecutorService executor;
    private final Supplier<T> supplier;

    /**
     * Constructor.
     * @param executor executor
     * @param supplier type of supplier
     */
    public AsyncSingleResultPublisher(ExecutorService executor, Supplier<T> supplier) {
        this.executor = executor;
        this.supplier = supplier;
    }

    /**
     * Constructor.
     * @param supplier type of supplier
     */
    public AsyncSingleResultPublisher(Supplier<T> supplier) {
        this(ForkJoinPool.commonPool(), supplier);
    }

    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber cannot be null");
        subscriber.onSubscribe(new ExecutorServiceSubscription<>(subscriber, supplier, executor));
    }

    /**
     * Subscription class.
     * @param <S> type of subscriber
     */
    static class ExecutorServiceSubscription<S> implements Subscription {
        private final Subscriber<? super S> subscriber;
        private final ExecutorService executor;
        private final Supplier<S> supplier;
        private Future<?> future; // to allow cancellation
        private boolean completed;

        /**
         * Constructor.
         * @param subscriber subscriber
         * @param supplier supplier
         * @param executor executor
         */
        ExecutorServiceSubscription(Subscriber<? super S> subscriber,
                                    Supplier<S> supplier,
                                    ExecutorService executor) {
            this.subscriber = subscriber;
            this.supplier = supplier;
            this.executor = executor;
        }

        /**
         * Request execution.
         * @param n request number
         */
        @Override
        public synchronized void request(long n) {
            if (n != 0 && !completed) {
                completed = true;
                if (n < 0) {
                    IllegalArgumentException ex = new IllegalArgumentException();
                    executor.execute(() -> subscriber.onError(ex));
                } else {
                    future = executor.submit(() -> {
                        try {
                            S value = supplier.get();
                            if (value != null) {
                                subscriber.onNext(value);
                            }
                            subscriber.onComplete();
                        } catch (Exception e) {
                            subscriber.onError(e);
                        }
                    });
                }
            }
        }

        /**
         * Cancel.
         */
        @Override
        public synchronized void cancel() {
            completed = true;
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
