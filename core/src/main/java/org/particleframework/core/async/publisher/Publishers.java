/*
 * Copyright 2017 original authors
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
package org.particleframework.core.async.publisher;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.async.subscriber.Emitter;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utilities for working with raw {@link Publisher} instances. Designed for internal use by Particle and
 * not as a replacement for a reactive library such as RxJava, Reactor, Akka etc.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class Publishers {

    /**
     * Build a {@link Publisher} from a {@link CompletableFuture}
     *
     * @param futureSupplier The supplier of the {@link CompletableFuture}
     * @param <T>
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> fromCompletableFuture(Supplier<CompletableFuture<T>> futureSupplier) {
        return new CompletableFuturePublisher<>(futureSupplier);
    }
    /**
     * Map the result from a publisher using the given mapper
     *
     * @param publisher The publisher
     * @param mapper The mapper
     * @param <T> The generic type
     * @param <R> The result type
     * @return The mapped publisher
     */
    public static <T,R> Publisher<R> map(Publisher<T> publisher, Function<T,R> mapper) {
        return actual -> publisher.subscribe(new CompletionAwareSubscriber<T>() {
            @Override
            protected void doOnSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            @Override
            protected void doOnNext(T message) {
                try {
                    R result = Objects.requireNonNull(mapper.apply(message),
                            "The mapper returned a null value.");
                    actual.onNext(result);
                } catch (Throwable e) {
                    onError(e);
                }

            }

            @Override
            protected void doOnError(Throwable t) {
                actual.onError(t);
            }

            @Override
            protected void doOnComplete() {
                actual.onComplete();
            }
        });
    }

    /**
     * Allow executing logic on completion of a Publisher
     *
     * @param publisher The publisher
     * @param future The runnable
     * @param <T> The generic type
     * @return The mapped publisher
     */
    public static <T> Publisher<T> onComplete(Publisher<T> publisher, Supplier<CompletableFuture<Void>> future) {
        return actual -> publisher.subscribe(new CompletionAwareSubscriber<T>() {
            @Override
            protected void doOnSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            @Override
            protected void doOnNext(T message) {
                try {
                    actual.onNext(message);
                } catch (Throwable e) {
                    onError(e);
                }

            }

            @Override
            protected void doOnError(Throwable t) {
                actual.onError(t);
            }

            @Override
            protected void doOnComplete() {
                future.get().whenComplete((aVoid, throwable) -> {
                    if(throwable != null) {
                        actual.onError(throwable);
                    }
                    else {
                        actual.onComplete();
                    }
                });
            }
        });
    }
}
