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
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An element of the body of a response, e.g. an event of an event stream or a piece of the body,
 * with the status and the headers of its response.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ElementResponse<T> implements HttpResponse<T>, AutoCloseable {

    private final HttpResponse<?> response;
    private final @Nullable T element;
    /**
     * Runs once: the discard hooks of a reactive chain may close a response more than once, and
     * what it releases, e.g. a single-subscriber publisher, may not be released twice.
     */
    private final AtomicReference<@Nullable Runnable> onClose;

    /**
     * @param response The response
     * @param element  The element, or {@code null} if the response has none
     */
    ElementResponse(HttpResponse<?> response, @Nullable T element) {
        this(response, element, null);
    }

    /**
     * @param response The response
     * @param element  The element, or {@code null} if the response has none
     * @param onClose  Releases what the element holds when nobody takes the response, e.g. the
     *                 body it is decoded from, or {@code null} if it holds nothing
     */
    ElementResponse(HttpResponse<?> response, @Nullable T element, @Nullable Runnable onClose) {
        this.response = response;
        this.element = element;
        this.onClose = new AtomicReference<>(onClose);
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
    public Optional<T> getBody() {
        return Optional.ofNullable(element);
    }

    /**
     * Release what the element holds, e.g. when nobody takes the response. Only the first call
     * releases it.
     */
    @Override
    public void close() {
        Runnable release = onClose.getAndSet(null);
        if (release != null) {
            release.run();
        }
    }
}
