/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.async.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Adapts a {@link CompletableFuture} to a {@link org.reactivestreams.Publisher}.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
class CompletableFuturePublisher<T> implements Publisher<T> {

    private final Supplier<CompletableFuture<T>> futureSupplier;

    /**
     * @param futureSupplier The function that supplies the future.
     */
    CompletableFuturePublisher(Supplier<CompletableFuture<T>> futureSupplier) {
        this.futureSupplier = futureSupplier;
    }

    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber cannot be null");
        subscriber.onSubscribe(new CompletableFutureSubscription(subscriber));
    }

    /**
     * CompletableFuture subscription.
     */
    class CompletableFutureSubscription implements Subscription {
        private final Subscriber<? super T> subscriber;
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private CompletableFuture<T> future; // to allow cancellation

        /**
         * @param subscriber The subscriber
         */
        CompletableFutureSubscription(Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
        }

        /**
         * @param n Number of elements to request to the upstream
         */
        public synchronized void request(long n) {
            if (n != 0 && !completed.get()) {
                if (n < 0) {
                    IllegalArgumentException ex = new IllegalArgumentException("Cannot request a negative number");
                    subscriber.onError(ex);
                } else {
                    try {
                        CompletableFuture<T> future = futureSupplier.get();
                        if (future == null) {
                            subscriber.onComplete();
                        } else {
                            this.future = future;
                            future.whenComplete((s, throwable) -> {
                                if (completed.compareAndSet(false, true)) {
                                    if (throwable != null) {
                                        subscriber.onError(throwable);
                                    } else {
                                        if (s != null) {
                                            subscriber.onNext(s);
                                        }
                                        subscriber.onComplete();
                                    }
                                }
                            });
                        }
                    } catch (Throwable e) {
                        subscriber.onError(e);
                    }
                }
            }
        }

        /**
         * Request the publisher to stop sending data and clean up resources.
         */
        public synchronized void cancel() {
            if (completed.compareAndSet(false, true)) {
                if (future != null) {
                    future.cancel(false);
                }
            }
        }
    }
}
