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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A wrapper around a {@link HttpResponse}.
 *
 * @param <B> The Http body type
 * @since 1.0.1
 */
public class HttpResponseWrapper<B> extends HttpMessageWrapper<B> implements HttpResponse<B> {

    /**
     * @param delegate The Http Request
     */
    public HttpResponseWrapper(HttpResponse<B> delegate) {
        super(delegate);
    }

    @Override
    public int code() {
        return getDelegate().code();
    }

    @Override
    public String reason() {
        return getDelegate().reason();
    }

    /**
     * Returns a mutable response with the status and headers of this response. If this response is,
     * or wraps, a {@link ByteBodyHttpResponse} whose bytes were not replaced by an object body, the
     * mutable response is a {@link MutableByteBodyHttpResponse} that takes over those bytes. If
     * this wrapper has an object body, it replaces the bytes of a wrapped response, which are
     * closed.
     *
     * @return The mutable response
     */
    @Override
    public MutableHttpResponse<?> toMutableResponse() {
        if (this instanceof MutableHttpResponse<?> mutable) {
            return mutable;
        }
        if (this instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
            return MutableByteBodyHttpResponse.of(byteBodyResponse);
        }
        ByteBodyHttpResponse<?> wrapped = wrappedByteBodyResponse(this);
        if (wrapped != null) {
            if (getBody().isPresent()) {
                // the object body of this wrapper replaces the bytes
                wrapped.close();
            } else if (wrapped.hasByteBody()) {
                // the status and headers of this wrapper, the bytes of the wrapped response
                return MutableByteBodyHttpResponse.of(this, wrapped.byteBody().move());
            }
        }
        return HttpResponse.super.toMutableResponse();
    }

    /**
     * The {@link ByteBodyHttpResponse} a wrapper wraps, directly or through other wrappers.
     *
     * @param wrapper The wrapper
     * @return The wrapped response, or {@code null}
     */
    @Internal
    public static @Nullable ByteBodyHttpResponse<?> wrappedByteBodyResponse(HttpResponseWrapper<?> wrapper) {
        HttpResponse<?> current = wrapper.getDelegate();
        while (true) {
            if (current instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                return byteBodyResponse;
            }
            if (current instanceof HttpResponseWrapper<?> next) {
                current = next.getDelegate();
            } else {
                return null;
            }
        }
    }

    @Override
    public HttpResponse<B> getDelegate() {
        HttpMessage<B> delegate = super.getDelegate();
        // this weird cast structure avoids type pollution
        return delegate instanceof MutableHttpResponse<B> mhr ? mhr : (HttpResponse<B>) delegate;
    }

}
