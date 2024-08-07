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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.reflect.ClassUtils;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Utilities for working with raw {@link Publisher} instances. Designed for internal use by Micronaut and
 * not as a replacement for a reactive library such as RxJava, Reactor, Akka etc.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@TypeHint(Publishers.class)
public class Publishers {

    private static final List<Class<?>> REACTIVE_TYPES;
    private static final List<Class<?>> SINGLE_TYPES;
    private static final List<Class<?>> COMPLETABLE_TYPES;

    static {
        List<Class<?>> reactiveTypes;
        List<Class<?>> singleTypes;
        List<Class<?>> completableTypes;
        ClassLoader classLoader = Publishers.class.getClassLoader();
        Optional<PublishersOptimizations> publishers = StaticOptimizations.get(PublishersOptimizations.class);
        if (publishers.isPresent()) {
            PublishersOptimizations optimizations = publishers.get();
            reactiveTypes = optimizations.getReactiveTypes();
            singleTypes = optimizations.getSingleTypes();
            completableTypes = optimizations.getCompletableTypes();
        } else {
            reactiveTypes = new ArrayList<>(3);
            singleTypes = new ArrayList<>(3);
            completableTypes = new ArrayList<>(3);
            for (String name : getNonSpecificReactiveTypeNames()) {
                Optional<Class<?>> aClass = ClassUtils.forName(name, classLoader);
                aClass.ifPresent(reactiveTypes::add);
            }
            for (String name : getSingleTypeNames()) {
                Optional<Class<?>> aClass = ClassUtils.forName(name, classLoader);
                aClass.ifPresent(aClass1 -> {
                    singleTypes.add(aClass1);
                    reactiveTypes.add(aClass1);
                });
            }

            for (String name : getCompletableTypeNames()) {
                Optional<Class<?>> aClass = ClassUtils.forName(name, classLoader);
                aClass.ifPresent(aClass1 -> {
                    completableTypes.add(aClass1);
                    reactiveTypes.add(aClass1);
                });
            }
        }
        REACTIVE_TYPES = reactiveTypes;
        SINGLE_TYPES = singleTypes;
        COMPLETABLE_TYPES = completableTypes;
    }

    @NonNull
    private static List<String> getSingleTypeNames() {
        return List.of(
            "io.micronaut.core.async.publisher.CompletableFuturePublisher",
            "io.micronaut.core.async.publisher.Publishers$JustPublisher",
            "io.micronaut.core.async.publisher.Publishers$JustThrowPublisher",
            "io.reactivex.Single",
            "reactor.core.publisher.Mono",
            "io.reactivex.Maybe",
            "io.reactivex.rxjava3.core.Single",
            "io.reactivex.rxjava3.core.Maybe"
        );
    }

    @NonNull
    private static List<String> getCompletableTypeNames() {
        return List.of(
            "io.reactivex.Completable",
            "io.reactivex.rxjava3.core.Completable",
            "io.micronaut.core.async.subscriber.Completable"
        );
    }

    @NonNull
    private static List<String> getNonSpecificReactiveTypeNames() {
        return List.of(
            "io.reactivex.Observable",
            "reactor.core.publisher.Flux",
            "kotlinx.coroutines.flow.Flow",
            "io.reactivex.rxjava3.core.Flowable",
            "io.reactivex.rxjava3.core.Observable"
        );
    }

    @NonNull
    public static List<String> getReactiveTypeNames() {
        return Stream.of(
            getNonSpecificReactiveTypeNames(),
            getSingleTypeNames(),
            getCompletableTypeNames(),
            List.of("org.reactivestreams.Publisher")
        ).flatMap(Collection::stream).toList();
    }

    /**
     * Registers an additional reactive type. Should be called during application static initialization.
     *
     * @param type The type
     * @since 2.0
     */
    public static void registerReactiveType(Class<?> type) {
        if (type != null) {
            REACTIVE_TYPES.add(type);
        }
    }

    /**
     * Registers an additional reactive single type. Should be called during application static initialization.
     *
     * @param type The type
     * @since 2.0
     */
    public static void registerReactiveSingle(Class<?> type) {
        if (type != null) {
            registerReactiveType(type);
            SINGLE_TYPES.add(type);
        }
    }

    /**
     * Registers an additional reactive completable type. Should be called during application static initialization.
     *
     * @param type The type
     * @since 2.0
     */
    public static void registerReactiveCompletable(Class<?> type) {
        if (type != null) {
            registerReactiveType(type);
            COMPLETABLE_TYPES.add(type);
        }
    }

    /**
     * @return A list of known reactive types.
     */
    public static List<Class<?>> getKnownReactiveTypes() {
        return List.copyOf(REACTIVE_TYPES);
    }

    /**
     * @return A list of known single types.
     */
    public static List<Class<?>> getKnownSingleTypes() {
        return List.copyOf(SINGLE_TYPES);
    }

    /**
     * @return A list of known single types.
     */
    public static List<Class<?>> getKnownCompletableTypes() {
        return List.copyOf(COMPLETABLE_TYPES);
    }

    /**
     * Build a {@link Publisher} from a {@link CompletableFuture}.
     *
     * @param futureSupplier The supplier of the {@link CompletableFuture}
     * @param <T> The type of the publisher
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> fromCompletableFuture(Supplier<CompletableFuture<T>> futureSupplier) {
        return new CompletableFuturePublisher<>(futureSupplier);
    }

    /**
     * Build a {@link Publisher} from a {@link CompletableFuture}.
     *
     * @param future The {@link CompletableFuture}
     * @param <T> The type of the publisher
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> fromCompletableFuture(CompletableFuture<T> future) {
        return new CompletableFuturePublisher<>(() -> future);
    }

    /**
     * A {@link Publisher} that emits a fixed single value.
     *
     * @param value The value to emit
     * @param <T> The value type
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> just(T value) {
        return new JustPublisher<>(value);
    }

    /**
     * A {@link Publisher} that emits a fixed single value.
     *
     * @param error The error to emit
     * @param <T> The value type
     * @return The {@link Publisher}
     */
    public static <T> Publisher<T> just(Throwable error) {
        return new JustThrowPublisher<>(error);
    }

    /**
     * A {@link Publisher} that completes without emitting any items.
     *
     * @param <T> The value type
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
     * @param mapper The mapper
     * @param <T> The generic type
     * @param <R> The result type
     * @return The mapped publisher
     */
    public static <T, R> Publisher<R> map(Publisher<T> publisher, Function<T, R> mapper) {
        return (MicronautPublisher<R>) actual -> publisher.subscribe(new CompletionAwareSubscriber<>() {
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
     * Map the result from a publisher using the given mapper or supply empty value.
     *
     * @param publisher The publisher
     * @param mapOrSupplyEmpty The mapOrSupplyEmpty
     * @param <T> The generic type
     * @param <R> The result type
     * @return The mapped publisher
     * @since 2.5.0
     */
    public static <T, R> Publisher<R> mapOrSupplyEmpty(Publisher<T> publisher, MapOrSupplyEmpty<T, R> mapOrSupplyEmpty) {
        return (MicronautPublisher<R>) actual -> publisher.subscribe(new CompletionAwareSubscriber<>() {

            final AtomicBoolean resultPresent = new AtomicBoolean();

            @Override
            protected void doOnSubscribe(Subscription subscription) {
                actual.onSubscribe(subscription);
            }

            @Override
            protected void doOnNext(T message) {
                try {
                    R result = Objects.requireNonNull(mapOrSupplyEmpty.map(message),
                        "The mapper returned a null value.");
                    actual.onNext(result);
                    resultPresent.set(true);
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
                if (!resultPresent.get()) {
                    actual.onNext(mapOrSupplyEmpty.supplyEmpty());
                }
                actual.onComplete();
            }
        });
    }

    /**
     * Map the result from a publisher using the given mapper.
     *
     * @param publisher The publisher
     * @param consumer The mapper
     * @param <T> The generic type
     * @return The mapped publisher
     */
    public static <T> Publisher<T> then(Publisher<T> publisher, Consumer<T> consumer) {
        return (MicronautPublisher<T>) actual -> publisher.subscribe(new CompletionAwareSubscriber<>() {
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
     * Allow executing logic on completion of a Publisher.
     *
     * @param publisher The publisher
     * @param future The runnable
     * @param <T> The generic type
     * @return The mapped publisher
     */
    public static <T> Publisher<T> onComplete(Publisher<T> publisher, Supplier<CompletableFuture<Void>> future) {
        return (MicronautPublisher<T>) actual -> publisher.subscribe(new CompletionAwareSubscriber<>() {
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
        // first, check for some common types without instanceof
        if (type == Publisher.class) {
            return true;
        }
        if (type.isPrimitive() || type.getName().startsWith("java.") || type.isArray()) {
            return false;
        }
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

    private static String packageOf(Class<?> type) {
        Package pkg = type.getPackage();
        if (pkg == null) {
            return "";
        }
        return pkg.getName();
    }

    /**
     * Is the given object a Publisher or convertible to a publisher.
     *
     * @param object The object
     * @return True if it is
     */
    public static boolean isConvertibleToPublisher(Object object) {
        if (object == null ||
            // check some common types for performance
            object instanceof String || object instanceof byte[]) {

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
     * @deprecated replaced by {@link #convertPublisher(ConversionService, Object, Class)}
     */
    @Deprecated(since = "4", forRemoval = true)
    public static <T> T convertPublisher(Object object, Class<T> publisherType) {
        return convertPublisher(ConversionService.SHARED, object, publisherType);
    }

    /**
     * Attempts to convert the publisher to the given type.
     *
     * @param conversionService The conversion service
     * @param object The object to convert
     * @param publisherType The publisher type
     * @param <T> The generic type
     * @return The Resulting in publisher
     * @since 4.0.0
     */
    public static <T> T convertPublisher(ConversionService conversionService, Object object, Class<T> publisherType) {
        Objects.requireNonNull(object, "Argument [object] cannot be null");
        Objects.requireNonNull(publisherType, "Argument [publisherType] cannot be null");
        if (publisherType.isInstance(object)) {
            return (T) object;
        }
        if (object instanceof CompletableFuture cf && !(object instanceof Publisher<?>)) {
            @SuppressWarnings("unchecked") Publisher<T> futurePublisher = Publishers.fromCompletableFuture(() -> cf);
            return conversionService.convert(futurePublisher, publisherType)
                .orElseThrow(() -> unconvertibleError(object, publisherType));
        }
        if (object instanceof MicronautPublisher && MicronautPublisher.class.isAssignableFrom(publisherType)) {
            return (T) object;
        }
        return conversionService.convert(object, publisherType)
            .orElseThrow(() -> unconvertibleError(object, publisherType));
    }

    /**
     * Attempts to convert the publisher to the given type.
     *
     * @param conversionService The conversion service
     * @param object The object to convert
     * @param <T> The generic type
     * @return The Resulting in publisher
     * @since 4.6.0
     */
    public static <T> Publisher<T> convertToPublisher(ConversionService conversionService, Object object) {
        Objects.requireNonNull(object, "Argument [object] cannot be null");
        if (object instanceof Publisher<?> publisher) {
            return (Publisher<T>) publisher;
        }
        if (object instanceof CompletableFuture cf) {
            return Publishers.fromCompletableFuture(() -> cf);
        }
        return conversionService.convert(object, Publisher.class)
            .orElseThrow(() -> unconvertibleError(object, Publisher.class));
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
     * Does the given reactive type emit a single result.
     *
     * @param type The type
     * @return True it does
     */
    public static boolean isCompletable(Class<?> type) {
        for (Class<?> reactiveType : COMPLETABLE_TYPES) {
            if (reactiveType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    private static <T> IllegalArgumentException unconvertibleError(Object object, Class<T> publisherType) {
        return new IllegalArgumentException("Cannot convert reactive type [" + object.getClass() + "] to type [" + publisherType + "]. Ensure that you have the necessary Reactive module on your classpath. For example for Reactor you should have 'micronaut-reactor'.");
    }

    /**
     * Maps the next result or supplies an empty result.
     *
     * @param <T> The next type
     * @param <R> The mapped to type
     * @since 2.5.0
     */
    public interface MapOrSupplyEmpty<T, R> {

        /**
         * Maps next result.
         *
         * @param result The next value.
         * @return The mapped value.
         */
        @NonNull
        R map(@NonNull T result);

        /**
         * Supplies an empty value if there is no next value.
         *
         * @return The result.
         */
        @NonNull
        R supplyEmpty();

    }

    /**
     * Marker interface for any micronaut produced publishers.
     *
     * @param <T> The generic type
     * @since 2.0.2
     */
    @SuppressWarnings("ReactiveStreamsPublisherImplementation")
    public interface MicronautPublisher<T> extends Publisher<T> {
    }

    /**
     * A publisher for a value. Needs to be public for micronaut-aot.
     *
     * @param <T> The type
     */
    @Internal
    public static class JustPublisher<T> implements MicronautPublisher<T> {
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
     * A publisher that throws an error. Needs to be public for micronaut-aot.
     *
     * @param <T> The type
     */
    @Internal
    public static class JustThrowPublisher<T> implements MicronautPublisher<T> {

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
    private static class JustCompletePublisher<T> implements MicronautPublisher<T> {

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
