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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * An element of the body of a response, e.g. an event of an event stream or a piece of the body,
 * with the status and the headers of its response.
 *
 * @param response The response
 * @param element  The element, or {@code null} if the response has none
 * @param <T>      The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record ElementResponse<T>(HttpResponse<?> response, @Nullable T element) implements HttpResponse<T> {
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
    public Optional<T> getBody() {
        return Optional.ofNullable(element);
    }
}
