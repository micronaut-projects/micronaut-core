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
import org.particleframework.core.reflect.ClassUtils;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
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

    static final List<Class<?>> reactiveTypes = new ArrayList<>(3);
    static final List<Class<?>> singleTypes = new ArrayList<>(3);
    static {
        ClassLoader classLoader = Publishers.class.getClassLoader();
        Publishers.singleTypes.add(CompletableFuturePublisher.class);
        Publishers.singleTypes.add(JustPublisher.class);
        List<String> typeNames = Arrays.asList(
                "io.reactivex.Observable",
                "reactor.core.publisher.Flux"
        );
        for (String name : typeNames) {
            Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            aClass.ifPresent(Publishers.reactiveTypes::add);
        }
        for (String name : Arrays.asList("io.reactivex.Single","reactor.core.publisher.Mono", "io.reactivex.Maybe")) {
            Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            aClass.ifPresent(aClass1 -> {
                Publishers.singleTypes.add(aClass1);
                Publishers.reactiveTypes.add(aClass1);
            });

        }
    }
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
     * A {@link Publisher} that emits a fixed single value
     * @param value The value to emit
     * @param <T> The value type
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> just(T value) {
        return new JustPublisher<>(value);
    }

    /**
     * A {@link Publisher} that emits a fixed single value
     * @param error The error to emit
     * @param <T> The value type
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> just(Throwable error) {
        return new JustThrowPublisher<>(error);
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
     * Map the result from a publisher using the given mapper
     *
     * @param publisher The publisher
     * @param consumer The mapper
     * @param <T> The generic type
     * @return The mapped publisher
     */
    public static <T> Publisher<T> then(Publisher<T> publisher, Consumer<T> consumer) {
        return actual -> publisher.subscribe(new CompletionAwareSubscriber<T>() {
            @Override
            protected void doOnSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            @Override
            protected void doOnNext(T message) {
                try {
                    consumer.accept(message);
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

    /**
     * Is the given type a Publisher or convertible to a publisher
     * @param type The type to check
     * @return True if it is
     */
    public static boolean isConvertibleToPublisher(Class<?> type) {
        if (Publisher.class.isAssignableFrom(type)) {
            return true;
        } else {
            for (Class<?> reactiveType : reactiveTypes) {
                if (reactiveType.isAssignableFrom(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Does the given reactive type emit a single result
     *
     * @param type The type
     * @return True it does
     */
    public static boolean isSingle(Class<?> type) {
        for (Class<?> reactiveType : singleTypes) {
            if (reactiveType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    private static class JustPublisher<T> implements Publisher<T> {
        private final T value;

        public JustPublisher(T value) {
            this.value = value;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                boolean done;
                @Override
                public void request(long n) {
                    if(done) return;
                    done = true;
                    subscriber.onNext(value);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }

    private static class JustThrowPublisher<T> implements Publisher<T> {

        private final Throwable error;

        public JustThrowPublisher(Throwable error) {
            this.error = error;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                boolean done;
                @Override
                public void request(long n) {
                    if(done) return;
                    done = true;
                    subscriber.onError(error);
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }
}
