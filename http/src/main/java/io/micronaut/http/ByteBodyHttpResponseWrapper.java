/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;

import java.util.Optional;


/**
 * Simple response wrapper to implement {@link ByteBodyHttpResponse}.
 *
 * @param <B> The original body type
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Experimental
public final class ByteBodyHttpResponseWrapper<B> extends HttpResponseWrapper<B> implements ByteBodyHttpResponse<B> {
    private final CloseableByteBody byteBody;

    private ByteBodyHttpResponseWrapper(HttpResponse<B> delegate, CloseableByteBody byteBody) {
        super(delegate);
        this.byteBody = byteBody;
    }

    /**
     * Attach a body to the given response.
     *
     * @param delegate The original response to be used for e.g. headers and status
     * @param byteBody The bytes to respond with
     * @return A {@link ByteBodyHttpResponse} implementation with the given response and bytes
     */
    public static ByteBodyHttpResponse<?> wrap(HttpResponse<?> delegate, CloseableByteBody byteBody) {
        return new ByteBodyHttpResponseWrapper<>(delegate, byteBody);
    }

    @Override
    public ByteBody byteBody() {
        return byteBody;
    }

    @Override
    public void close() {
        byteBody.close();
    }

    // the attributes are those of the delegate (see getAttributes), which may answer a read
    // without creating its attribute map

    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        return getDelegate().getAttribute(name);
    }

    @Override
    public <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        return getDelegate().getAttribute(name, type);
    }
}
