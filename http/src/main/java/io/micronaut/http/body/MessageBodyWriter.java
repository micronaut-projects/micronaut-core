/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * An interface that allows writing a message body for the client or the server.
 *
 * <p>Implementors can define beans that use {@link io.micronaut.http.annotation.Produces} to restrict the applicable content types.</p>
 *
 * @param <T> The generic type.
 * @see io.micronaut.http.annotation.Produces
 * @since 4.0.0
 */
@Indexed(MessageBodyWriter.class)
public interface MessageBodyWriter<T> extends Ordered {
    /**
     * Is the type writeable.
     *
     * @param type      The type
     * @param mediaType The media type, can  be {@code null}
     * @return True if is readable
     */
    default boolean isWriteable(@NonNull Argument<T> type, @Nullable MediaType mediaType) {
        return true;
    }

    /**
     * Writes an object to the given output stream.
     *
     * @param type         The type being decoded.
     * @param object       The object to write
     * @param mediaType    The media type, can  be {@code null}
     * @param outgoingHeaders  The HTTP headers
     * @param outputStream The output stream
     * @throws CodecException If an error occurs decoding
     */
    // todo: "bake" with known argument type
    void writeTo(
        @NonNull Argument<T> type,
        T object,
        @NonNull MediaType mediaType,
        @NonNull MutableHeaders outgoingHeaders,
        @NonNull OutputStream outputStream) throws CodecException;

    /**
     * Writes an object to the given stream.
     *
     * @param type        The type being decoded.
     * @param object      The object to write
     * @param mediaType   The media type, can  be {@code null}
     * @param outgoingHeaders The HTTP headers
     * @param bufferFactory A byte buffer factory
     * @throws CodecException If an error occurs decoding
     */
    @NonNull
    default ByteBuffer<?> writeTo(
        @NonNull Argument<T> type,
        T object,
        @NonNull MediaType mediaType,
        @NonNull MutableHeaders outgoingHeaders,
        @NonNull ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        ByteBuffer<?> buffer = bufferFactory.buffer();
        writeTo(type, object, mediaType, outgoingHeaders, buffer.toOutputStream());
        return buffer;
    }

    /**
     * Resolve the charset.
     * @param headers The headers
     * @return The charset
     */
    default @NonNull Charset getCharset(@NonNull Headers headers) {
        if (headers instanceof HttpHeaders httpHeaders) {
            Charset charset = httpHeaders.acceptCharset();
            if (charset != null) {
                return charset;
            }
        }
        return StandardCharsets.UTF_8;
    }
}
