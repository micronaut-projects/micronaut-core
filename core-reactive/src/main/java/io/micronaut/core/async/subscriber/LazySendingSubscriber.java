/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.async.subscriber;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

/**
 * This class waits for the first item of a publisher before completing an ExecutionFlow with a
 * publisher containing the same items.
 *
 * @param <T> The publisher item type
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class LazySendingSubscriber<T> implements CoreSubscriber<T>, CorePublisher<T>, Subscription {
    private final DelayedExecutionFlow<Publisher<T>> result = DelayedExecutionFlow.create();
    private boolean receivedFirst = false;
    private volatile boolean sentFirst = false;
    private boolean sendingFirst = false;
    private T first;
    private Subscription upstream;
    private volatile CoreSubscriber<? super T> downstream;
    private Signal<? extends T> heldBackSignal;
    private long heldBackDemand = 0;

    private LazySendingSubscriber() {
    }

    /**
     * Create an {@link ExecutionFlow} that waits for the first item of the given publisher. If
     * there is an error before the first item, the flow will fail. If there is no error, the flow
     * will complete with a publisher containing all items, including the first one.
     *
     * @param input The input stream
     * @return A flow that will complete with the same stream
     * @param <T> The item type
     */
    @NonNull
    public static <T> ExecutionFlow<Publisher<T>> create(@NonNull Publisher<T> input) {
        LazySendingSubscriber<T> subscriber = new LazySendingSubscriber<>();
        input.subscribe(subscriber);
        return subscriber.result;
    }

    @Override
    public Context currentContext() {
        return downstream == null ? Context.empty() : downstream.currentContext();
    }

    @Override
    public void onSubscribe(Subscription s) {
        upstream = s;
        s.request(1);
    }

    @Override
    public void onNext(T t) {
        if (!receivedFirst) {
            receivedFirst = true;
            first = t;
            result.complete(this);
        } else {
            downstream.onNext(t);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (receivedFirst) {
            Subscriber<? super T> d;
            synchronized (this) {
                d = downstream;
                if (d == null || !sentFirst) {
                    heldBackSignal = Signal.error(t);
                    return;
                }
            }
            d.onError(t);
        } else {
            receivedFirst = true;
            result.completeExceptionally(t);
        }
    }

    @Override
    public void onComplete() {
        if (!receivedFirst) {
            onNext(null);
        }

        Subscriber<? super T> d;
        synchronized (this) {
            d = downstream;
            if (d == null || !sentFirst) {
                heldBackSignal = Signal.complete();
                return;
            }
        }
        d.onComplete();
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> subscriber) {
        synchronized (this) {
            downstream = subscriber;
        }
        subscriber.onSubscribe(this);
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        subscribe(Operators.toCoreSubscriber(s));
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        if (sum < a) {
            return Long.MAX_VALUE;
        }
        return sum;
    }

    @Override
    public void request(long n) {
        if (!sentFirst) {
            if (sendingFirst) {
                // we're currently running onNext, need to wait with the request() call.
                synchronized (this) {
                    if (!sentFirst) {
                        // hold back demand until onNext is done
                        heldBackDemand = saturatingAdd(heldBackDemand, n);
                        return;
                    }
                }
                // sentFirst became true
                upstream.request(n);
                return;
            }
            sendingFirst = true;
            if (first != null) {
                downstream.onNext(first); // note: this can trigger reentrancy!
                first = null;
            }
            Signal<? extends T> heldBackSignal;
            synchronized (this) {
                sentFirst = true;
                heldBackSignal = this.heldBackSignal;
                n = saturatingAdd(n, heldBackDemand);
            }
            if (heldBackSignal != null) {
                heldBackSignal.accept(downstream);
                return;
            }
            n--;
            if (n <= 0) {
                return;
            }
        }

        upstream.request(n);
    }

    @Override
    public void cancel() {
        if (!sentFirst) {
            sentFirst = true;
            T t = first;
            first = null;
            Operators.onNextDropped(t, currentContext());
        }
        upstream.cancel();
    }
}
