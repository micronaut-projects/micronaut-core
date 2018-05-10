/*
 * Copyright 2017-2018 original authors
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

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Adapts a {@link CompletableFuture} to a {@link org.reactivestreams.Publisher}.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
class CompletableFuturePublisher<T> extends SingleSubscriberPublisher<T> {

    private final Supplier<CompletableFuture<T>> futureSupplier;
    private final Queue<BiConsumer<? super T, ? super Throwable>> whenCompletes = new ConcurrentLinkedDeque<>();

    /**
     * @param futureSupplier The function that supplies the future.
     */
    CompletableFuturePublisher(Supplier<CompletableFuture<T>> futureSupplier) {
        this.futureSupplier = futureSupplier;
    }

    /**
     * Allow execution of callbacks when the publisher completes.
     *
     * @param action The action
     * @return This publisher
     */
    public CompletableFuturePublisher<T> whenComplete(
        BiConsumer<? super T, ? super Throwable> action) {
        if (action != null) {
            whenCompletes.add(action);
        }
        return this;
    }

    @Override
    protected void doSubscribe(Subscriber<? super T> subscriber) {
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
                    CompletableFuture<T> future = futureSupplier.get();
                    if (future == null) {
                        subscriber.onComplete();
                    } else {
                        for (BiConsumer<? super T, ? super Throwable> whenComplete : whenCompletes) {
                            future = future.whenComplete(whenComplete);
                        }
                        this.future = future;
                        future.whenComplete((s, throwable) -> {
                            if (completed.compareAndSet(false, true)) {
                                if (throwable != null) {
                                    subscriber.onError(throwable);
                                } else {

                                    subscriber.onNext(s);
                                    subscriber.onComplete();
                                }
                            }
                        });
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
