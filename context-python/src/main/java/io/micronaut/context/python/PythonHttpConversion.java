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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import java.util.Optional;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

/**
 * Conversion of Python return values that are HTTP responses or reactive publishers.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonHttpConversion {

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
}
