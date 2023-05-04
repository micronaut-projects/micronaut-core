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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

import java.io.IOException;
import java.io.InputStream;

/**
 * An interface that allows reading a message body from the client or the server.
 *
 * <p>Implementors can defined beans that are annotated with {@link io.micronaut.http.annotation.Consumes} to restrict the applicable content types.</p>
 *
 * @see io.micronaut.http.annotation.Consumes
 * @param <T> The generic type.
 * @since 4.0.0
 */
@Experimental
@Indexed(MessageBodyReader.class)
public interface MessageBodyReader<T> extends Ordered {
    /**
     * Is the type readable.
     * @param type The type
     * @param mediaType The media type, can be {@code null}
     * @return True if is readable
     */
    default boolean isReadable(@NonNull Argument<T> type, @Nullable MediaType mediaType) {
        return true;
    }

    /**
     * Reads an object from the given byte buffer.
     *
     * @param type The type being decoded.
     * @param mediaType The media type, can be {@code null}
     * @param httpHeaders The HTTP headers
     * @param byteBuffer The byte buffer
     * @return The read object or {@code null}
     * @throws CodecException If an error occurs decoding
     */
    default @Nullable T read(
        @NonNull Argument<T> type,
        @Nullable MediaType mediaType,
        @NonNull Headers httpHeaders,
        @NonNull ByteBuffer<?> byteBuffer) throws CodecException {
        T read;
        try (InputStream inputStream = byteBuffer.toInputStream()) {
            read = read(type, mediaType, httpHeaders, inputStream);
        } catch (IOException e) {
            throw new CodecException("Error reading message body: " + e.getMessage(), e);
        }
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return read;
    }

    /**
     * Reads an object from the given byte buffer.
     *
     * @param type The type being decoded.
     * @param mediaType The media type, can be {@code null}
     * @param httpHeaders The HTTP headers
     * @param inputStream The input stream
     * @return The read object or {@code null}
     * @throws CodecException If an error occurs decoding
     */
    @Nullable T read(
        @NonNull Argument<T> type,
        @Nullable MediaType mediaType,
        @NonNull Headers httpHeaders,
        @NonNull InputStream inputStream) throws CodecException;
}
