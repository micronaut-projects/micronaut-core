/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Conversion of Python return values that are HTTP responses or reactive publishers.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonHttpConversion {

    private static final boolean REACTOR_AVAILABLE = ClassUtils.isPresent("reactor.core.publisher.Mono", PythonHttpConversion.class.getClassLoader());

    private PythonHttpConversion() {
    }

    /**
     * Convert a GraalPy-created {@link HttpResponse} and its response body to the declared Java body type.
     *
     * @param value The source polyglot response
     * @param bodyType The declared response body type
     * @param <T> The response body type
     * @return The converted response, or {@code null} when the value is not an {@link HttpResponse}
     */
    @SuppressWarnings({"unchecked", "NullAway"})
    public static <T> @Nullable HttpResponse<T> convertHttpResponse(Value value, Class<T> bodyType) {
        HttpResponse<?> response = PythonConversion.convertValue(value, HttpResponse.class);
        if (response == null) {
            return null;
        }
        return convertHttpResponse(response, bodyType);
    }

    /**
     * Convert each item emitted by a Python-returned publisher to the declared Java item type.
     *
     * @param publisher The source publisher
     * @param itemType The declared publisher item type
     * @param <T> The item type
     * @return The converted publisher
     */
    @SuppressWarnings("unchecked")
    public static <T> Publisher<T> convertPublisher(Publisher<?> publisher, Class<T> itemType) {
        return Publishers.map((Publisher<Object>) publisher, item -> convertPublishedValue(item, itemType));
    }

    /**
     * Convert publisher items with a generated wrapper converter. A generated converter preserves
     * the concrete Python wrapper type after the publisher crosses the reactive boundary.
     *
     * @param publisher The source publisher
     * @param converter The generated item converter
     * @param <T> The item type
     * @return The converted publisher
     */
    @SuppressWarnings("unchecked")
    public static <T> Publisher<T> convertPublisher(Publisher<?> publisher, PolyglotValueConverter<T> converter) {
        return Publishers.map((Publisher<Object>) publisher, item -> {
            if (item == null) {
                return null;
            }
            if (item instanceof Value value) {
                return converter.convert(value);
            }
            if (item instanceof ValueCoercible valueCoercible) {
                return converter.convert(valueCoercible.asPolyglotValue());
            }
            return (T) item;
        });
    }

    /**
     * Convert a GraalPy Value representing a publisher to a typed Java publisher.
     *
     * @param value The source polyglot publisher
     * @param itemType The declared publisher item type
     * @param <T> The item type
     * @return The converted publisher
     */
    public static <T> @Nullable Publisher<T> convertPublisher(Value value, Class<T> itemType) {
        Publisher<?> publisher = PythonConversion.convertValue(value, Publisher.class);
        if (publisher == null) {
            return null;
        }
        return convertPublisher(publisher, itemType);
    }

    /**
     * Convert a GraalPy publisher with a generated item converter.
     *
     * @param value The source polyglot publisher
     * @param converter The generated item converter
     * @param <T> The item type
     * @return The converted publisher
     */
    public static <T> @Nullable Publisher<T> convertPublisher(Value value, PolyglotValueConverter<T> converter) {
        Publisher<?> publisher = PythonConversion.convertValue(value, Publisher.class);
        if (publisher == null) {
            return null;
        }
        return convertPublisher(publisher, converter);
    }

    /**
     * Convert a GraalPy value representing a publisher to the reactive type a method declares: the
     * items are converted to the declared item type and the publisher is then adapted to the declared
     * type ({@code Mono}, {@code Flux}, a {@code CompletionStage} or any reactive type the conversion
     * service knows), so a Python body may return a {@code Flux} where the Java signature says
     * {@code Mono}, or a {@code Mono} where it says {@code CompletableFuture}.
     *
     * @param value The source polyglot publisher
     * @param itemType The declared publisher item type
     * @param targetType The declared reactive return type
     * @param <T> The item type
     * @param <P> The reactive type
     * @return The converted publisher, or {@code null} for {@code None}
     */
    public static <T, P> @Nullable P convertPublisher(Value value, Class<T> itemType, Class<P> targetType) {
        return convertReactive(convertPublisher(value, itemType), targetType);
    }

    /**
     * Convert a GraalPy publisher with a generated item converter to the reactive type a method declares.
     *
     * @param value The source polyglot publisher
     * @param converter The generated item converter
     * @param targetType The declared reactive return type
     * @param <T> The item type
     * @param <P> The reactive type
     * @return The converted publisher, or {@code null} for {@code None}
     * @see #convertPublisher(Value, Class, Class)
     */
    public static <T, P> @Nullable P convertPublisher(Value value, PolyglotValueConverter<T> converter, Class<P> targetType) {
        return convertReactive(convertPublisher(value, converter), targetType);
    }

    /**
     * Adapt a reactive value (a {@link Publisher} or a {@link CompletionStage}) to the reactive type a
     * method declares. A value that already is of the declared type is returned as is; a publisher is
     * otherwise wrapped as {@code Mono.from}/{@code Flux.from} when Reactor is present, completed into
     * a future when a {@link CompletionStage} is declared, and converted through the shared
     * {@link ConversionService} for any other reactive type; a stage is adapted through the publisher
     * it completes.
     *
     * @param source The publisher or completion stage
     * @param targetType The declared reactive return type
     * @param <P> The reactive type
     * @return The adapted value, or {@code null} for a {@code null} source
     * @throws IllegalArgumentException When the value cannot be adapted to the declared type
     */
    @SuppressWarnings("unchecked")
    public static <P> @Nullable P convertReactive(@Nullable Object source, Class<P> targetType) {
        if (source == null || targetType.isInstance(source)) {
            return (P) source;
        }
        boolean futureTarget = CompletionStage.class.isAssignableFrom(targetType) && targetType.isAssignableFrom(CompletableFuture.class);
        Object reactive = source;
        if (reactive instanceof CompletionStage<?> stage) {
            if (futureTarget) {
                return targetType.cast(stage.toCompletableFuture());
            }
            reactive = Publishers.fromCompletableFuture(stage.toCompletableFuture());
        }
        if (reactive instanceof Publisher<?> publisher) {
            if (targetType.isInstance(publisher)) {
                return targetType.cast(publisher);
            }
            if (futureTarget) {
                return targetType.cast(PythonCoercion.scalarFuture(publisher));
            }
            if (REACTOR_AVAILABLE) {
                P reactor = Reactor.adapt(publisher, targetType);
                if (reactor != null) {
                    return reactor;
                }
            }
            return Publishers.convertPublisher(ConversionService.SHARED, publisher, targetType);
        }
        throw new IllegalArgumentException("Cannot convert value [" + source + "] of type [" + source.getClass().getName() + "] to reactive type [" + targetType.getName() + "]");
    }

    /**
     * Convert a response body to the declared Java body type.
     *
     * @param response The source response
     * @param bodyType The declared response body type
     * @param <T> The response body type
     * @return The converted response
     */
    @SuppressWarnings("unchecked")
    public static <T> HttpResponse<T> convertHttpResponse(HttpResponse<?> response, Class<T> bodyType) {
        Optional<?> body = response.getBody();
        if (body.isEmpty()) {
            return (HttpResponse<T>) response;
        }
        Object rawBody = body.get();
        if (rawBody == null) {
            return (HttpResponse<T>) response;
        }
        if (response instanceof MutableHttpResponse<?> mutableResponse) {
            T convertedBody = convertResponseBody(rawBody, bodyType);
            if (convertedBody == null) {
                return (HttpResponse<T>) response;
            }
            return mutableResponse.body(convertedBody);
        }
        return (HttpResponse<T>) response;
    }

    private static <T> @Nullable T convertPublishedValue(@Nullable Object item, Class<T> itemType) {
        if (item == null || itemType.isInstance(item)) {
            return itemType.cast(item);
        }
        switch (item) {
            case HttpResponse<?> response when HttpResponse.class.isAssignableFrom(itemType) -> {
                return itemType.cast(convertHttpResponse(response, Object.class));
            }
            case Value value -> {
                return PythonConversion.convertValue(value, itemType);
            }
            default -> {
                try {
                    return PythonConversion.convertValue(Value.asValue(item), itemType);
                } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
                    return itemType.cast(item);
                }
            }
        }
    }

    private static <T> @Nullable T convertResponseBody(Object rawBody, Class<T> bodyType) {
        if (Object.class.equals(bodyType)) {
            @SuppressWarnings("unchecked")
            T converted = (T) PythonConversion.convertObjectResponseBody(rawBody);
            return converted;
        }
        if (bodyType.isInstance(rawBody)) {
            return bodyType.cast(rawBody);
        }
        if (rawBody instanceof ProxyObject proxyObject && proxyObject.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            T converted = PythonConversion.convertValueCoercibleProxy(proxyObject, bodyType);
            if (converted != null) {
                return converted;
            }
        }
        if (rawBody instanceof Value bodyValue) {
            return PythonConversion.convertValue(bodyValue, bodyType);
        }
        try {
            return PythonConversion.convertValue(Value.asValue(rawBody), bodyType);
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * Reactor adaptation, only touched when Reactor is on the class path.
     */
    private static final class Reactor {

        private Reactor() {
        }

        @SuppressWarnings("unchecked")
        static <P> @Nullable P adapt(Publisher<?> publisher, Class<P> targetType) {
            if (targetType.isAssignableFrom(Mono.class)) {
                return (P) Mono.from(publisher);
            }
            if (targetType.isAssignableFrom(Flux.class)) {
                return (P) Flux.from(publisher);
            }
            return null;
        }
    }
}
