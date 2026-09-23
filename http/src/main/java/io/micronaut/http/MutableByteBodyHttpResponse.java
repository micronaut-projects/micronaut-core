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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.body.CloseableByteBody;

/**
 * A {@link MutableHttpResponse} that carries already encoded body bytes, for example a response
 * received by the raw HTTP client and relayed by the server. Status and headers can be changed
 * (e.g. by response filters) while the {@link #byteBody() bytes} are passed through unchanged.
 * <p>Setting an object body through {@link #body(Object)} replaces the bytes: the byte body is
 * closed, and the response is then encoded like any other {@link MutableHttpResponse}. Setting a
 * {@code null} body on a response that still carries its bytes has no effect.
 * <p>Like any {@link ByteBodyHttpResponse}, this response must be {@link #close() closed} if its
 * bytes are not used.
 *
 * @param <B> The object body type
 * @since 5.3.0
 */
@Experimental
public interface MutableByteBodyHttpResponse<B> extends MutableHttpResponse<B>, ByteBodyHttpResponse<B> {

    @Override
    default MutableHttpResponse<?> toMutableResponse() {
        return this;
    }

    /**
     * Create a mutable response that takes over the given response and its bytes. Closing the
     * returned response closes the given one.
     *
     * @param response The response carrying the bytes
     * @return The mutable response, or the given response if it is already mutable
     */
    static MutableByteBodyHttpResponse<?> of(ByteBodyHttpResponse<?> response) {
        if (response instanceof MutableByteBodyHttpResponse<?> mutable) {
            return mutable;
        }
        return new DefaultMutableByteBodyHttpResponse<>(response);
    }

    /**
     * Create a mutable response with the status and headers of the given response and the given
     * bytes.
     *
     * @param response The response providing the status and headers
     * @param body     The body bytes. Ownership transfers to the returned response
     * @return The mutable response
     */
    static MutableByteBodyHttpResponse<?> of(HttpResponse<?> response, CloseableByteBody body) {
        return new DefaultMutableByteBodyHttpResponse<>(ByteBodyHttpResponseWrapper.wrap(response, body));
    }
}
