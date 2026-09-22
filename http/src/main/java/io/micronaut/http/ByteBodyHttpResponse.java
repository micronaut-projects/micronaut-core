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

import java.io.Closeable;

/**
 * Special response type that contains the encoded response bytes. Responses of this type must also
 * be closed if their {@link #byteBody()} is not used.
 *
 * @param <B> The original (non-encoded) body type
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Experimental
public interface ByteBodyHttpResponse<B> extends HttpResponse<B>, Closeable {
    /**
     * The body bytes.
     *
     * @return The bytes
     */
    ByteBody byteBody();

    /**
     * Close this response.
     */
    @Override
    void close();

    /**
     * Whether the {@link #byteBody() bytes} still are the body of this response. They are not once
     * an object body replaced them, e.g. with {@link MutableHttpResponse#body(Object)} on a
     * {@link MutableByteBodyHttpResponse}: then the bytes are closed, and the response is encoded
     * from its object body, if any.
     *
     * @return {@code true} if the bytes are the body of this response
     * @since 5.3.0
     */
    default boolean hasByteBody() {
        return getBody().isEmpty();
    }

    /**
     * Returns a mutable response that keeps the {@link #byteBody() body bytes} of this response.
     * The returned response takes over the ownership of this response: closing it closes this one.
     *
     * @return The mutable response
     * @since 5.3.0
     */
    @Override
    default MutableHttpResponse<?> toMutableResponse() {
        return MutableByteBodyHttpResponse.of(this);
    }
}
