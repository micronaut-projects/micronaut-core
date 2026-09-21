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
     * mutable response is a {@link MutableByteBodyHttpResponse} that takes over those bytes.
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
        if (getBody().isEmpty()) {
            HttpResponse<?> current = getDelegate();
            while (true) {
                if (current instanceof ByteBodyHttpResponse<?> byteBodyResponse) {
                    if (byteBodyResponse.getBody().isEmpty()) {
                        // the status and headers of this wrapper, the bytes of the wrapped response
                        return MutableByteBodyHttpResponse.of(this, byteBodyResponse.byteBody().move());
                    }
                    break;
                }
                if (current instanceof HttpResponseWrapper<?> wrapper) {
                    current = wrapper.getDelegate();
                } else {
                    break;
                }
            }
        }
        return HttpResponse.super.toMutableResponse();
    }

    @Override
    public HttpResponse<B> getDelegate() {
        HttpMessage<B> delegate = super.getDelegate();
        // this weird cast structure avoids type pollution
        return delegate instanceof MutableHttpResponse<B> mhr ? mhr : (HttpResponse<B>) delegate;
    }

}
