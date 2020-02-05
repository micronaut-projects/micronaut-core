/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utilities for working with raw {@link Publisher} instances. Designed for internal use by Micronaut and
 * not as a replacement for a reactive library such as RxJava, Reactor, Akka etc.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class Publishers {

    @SuppressWarnings("ConstantName")
    private static final List<Class<?>> REACTIVE_TYPES = new ArrayList<>(3);
    @SuppressWarnings("ConstantName")
    private static final List<Class<?>> SINGLE_TYPES = new ArrayList<>(3);

    static {
        ClassLoader classLoader = Publishers.class.getClassLoader();
        Publishers.SINGLE_TYPES.add(CompletableFuturePublisher.class);
        Publishers.SINGLE_TYPES.add(JustPublisher.class);
        List<String> typeNames = Arrays.asList(
            "io.reactivex.Observable",
            "reactor.core.publisher.Flux",
            "kotlinx.coroutines.flow.Flow"
        );
        for (String name : typeNames) {
            Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            aClass.ifPresent(Publishers.REACTIVE_TYPES::add);
        }
        for (String name : Arrays.asList(
                "io.reactivex.Single",
                "reactor.core.publisher.Mono",
                "io.reactivex.Maybe",
                "io.reactivex.Completable")) {
            Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            aClass.ifPresent(aClass1 -> {
                Publishers.SINGLE_TYPES.add(aClass1);
                Publishers.REACTIVE_TYPES.add(aClass1);
            });
        }
    }

    /**
     * @return A list of known reactive types.
     */
    public static List<Class<?>> getKnownReactiveTypes() {
        return Collections.unmodifiableList(REACTIVE_TYPES);
    }

    /**
     * Build a {@link Publisher} from a {@link CompletableFuture}.
     *
     * @param futureSupplier The supplier of the {@link CompletableFuture}
     * @param <T>            The type of the publisher
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> fromCompletableFuture(Supplier<CompletableFuture<T>> futureSupplier) {
        return new CompletableFuturePublisher<>(futureSupplier);
    }

    /**
     * Build a {@link Publisher} from a {@link CompletableFuture}.
     *
     * @param future The {@link CompletableFuture}
     * @param <T>  The type of the publisher
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> fromCompletableFuture(CompletableFuture<T> future) {
        return new CompletableFuturePublisher<>(() -> future);
    }

    /**
     * A {@link Publisher} that emits a fixed single value.
     *
     * @param value The value to emit
     * @param <T>   The value type
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> just(T value) {
        return new JustPublisher<>(value);
    }

    /**
     * A {@link Publisher} that emits a fixed single value.
     *
     * @param error The error to emit
     * @param <T>   The value type
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> just(Throwable error) {
        return new JustThrowPublisher<>(error);
    }

    /**
     * A {@link Publisher} that completes without emitting any items.
     *
     * @param <T>   The value type
     * @return The {@link Publisher}
     * @since 2.0.0
     */
    public static <T> Publisher<T> empty() {
        return new JustCompletePublisher<>();
    }

    /**
     * Map the result from a publisher using the given mapper.
     *
     * @param publisher The publisher
     * @param mapper    The mapper
     * @param <T>       The generic type
     * @param <R>       The result type
     * @return The mapped publisher
     */
    public static <T, R> Publisher<R> map(Publisher<T> publisher, Function<T, R> mapper) {
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
     * Map the result from a publisher using the given mapper.
     *
     * @param publisher The publisher
     * @param consumer  The mapper
     * @param <T>       The generic type
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
                    actual.onNext(message);
                    consumer.accept(message);
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
     * Allow executing logic on completion of a Publisher.
     *
     * @param publisher The publisher
     * @param future    The runnable
     * @param <T>       The generic type
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
                    if (throwable != null) {
                        actual.onError(throwable);
                    } else {
                        actual.onComplete();
                    }
                });
            }
        });
    }

    /**
     * Is the given type a Publisher or convertible to a publisher.
     *
     * @param type The type to check
     * @return True if it is
     */
    public static boolean isConvertibleToPublisher(Class<?> type) {
        if (Publisher.class.isAssignableFrom(type)) {
            return true;
        } else {
            for (Class<?> reactiveType : REACTIVE_TYPES) {
                if (reactiveType.isAssignableFrom(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Is the given object a Publisher or convertible to a publisher.
     *
     * @param object The object
     * @return True if it is
     */
    public static boolean isConvertibleToPublisher(Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Publisher) {
            return true;
        } else {
            return isConvertibleToPublisher(object.getClass());
        }
    }

    /**
     * Attempts to convert the publisher to the given type.
     *
     * @param object The object to convert
     * @param publisherType The publisher type
     * @param <T> The generic type
     * @return The Resulting in publisher
     */
    public static <T> T convertPublisher(Object object, Class<T> publisherType) {
        Objects.requireNonNull(object, "Argument [object] cannot be null");
        Objects.requireNonNull(publisherType, "Argument [publisherType] cannot be null");
        if (object instanceof CompletableFuture) {
            @SuppressWarnings("unchecked") Publisher<T> futurePublisher = (Publisher<T>) Publishers.fromCompletableFuture(() -> ((CompletableFuture) object));
            return ConversionService.SHARED.convert(futurePublisher, publisherType)
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported Reactive type: " + object.getClass()));
        } else {
            return ConversionService.SHARED.convert(object, publisherType)
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported Reactive type: " + object.getClass()));
        }
    }


    /**
     * Does the given reactive type emit a single result.
     *
     * @param type The type
     * @return True it does
     */
    public static boolean isSingle(Class<?> type) {
        for (Class<?> reactiveType : SINGLE_TYPES) {
            if (reactiveType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A publisher for a value.
     *
     * @param <T> The type
     */
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
                    if (done) {
                        return;
                    }
                    done = true;
                    if (value != null) {
                        subscriber.onNext(value);
                    }
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }

    /**
     * A publisher that throws an error.
     *
     * @param <T> The type
     */
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
                    if (done) {
                        return;
                    }
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

    /**
     * A publisher that completes without emitting any items.
     *
     * @param <T> The type
     */
    private static class JustCompletePublisher<T> implements Publisher<T> {

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                boolean done;

                @Override
                public void request(long n) {
                    if (done) {
                        return;
                    }
                    done = true;
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }
}
