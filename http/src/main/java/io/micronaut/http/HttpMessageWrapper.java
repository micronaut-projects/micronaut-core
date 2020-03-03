/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;

import java.util.Optional;

/**
 * A wrapper around an {@link HttpMessage}.
 *
 * @param <B> The message body
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpMessageWrapper<B> implements HttpMessage<B> {

    private final HttpMessage<B> delegate;

    /**
     * @param delegate The Http message
     */
    public HttpMessageWrapper(HttpMessage<B> delegate) {
        this.delegate = delegate;
    }

    /**
     * @return The Http message
     */
    public HttpMessage<B> getDelegate() {
        return delegate;
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Optional<B> getBody() {
        return delegate.getBody();
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        return delegate.getBody(type);
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return delegate.getBody(type);
    }
}
