/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;

import java.util.Optional;

/**
 * The response of an asynchronous streaming exchange: the status and the headers of the
 * response, and the elements of its body as the body.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ElementsResponse<T> implements HttpResponse<BodyElements<T>> {

    private final HttpResponse<?> response;
    private final BodyElements<T> elements;

    private ElementsResponse(HttpResponse<?> response, BodyElements<T> elements) {
        this.response = response;
        this.elements = elements;
    }

    /**
     * @param response The response, for its status, headers and attributes
     * @param elements The elements of the body
     * @param <T>      The type of an element
     * @return The response with the elements as its body
     */
    public static <T> HttpResponse<BodyElements<T>> of(HttpResponse<?> response, BodyElements<T> elements) {
        return new ElementsResponse<>(response, elements);
    }

    @Override
    public int code() {
        return response.code();
    }

    @Override
    public String reason() {
        return response.reason();
    }

    @Override
    public HttpHeaders getHeaders() {
        return response.getHeaders();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return response.getAttributes();
    }

    @Override
    public Optional<BodyElements<T>> getBody() {
        return Optional.of(elements);
    }
}
