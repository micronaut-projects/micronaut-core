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
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
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
@Experimental
@Indexed(MessageBodyWriter.class)
public interface MessageBodyWriter<T> {
    /**
     * Is the type writeable.
     *
     * @param type      The type
     * @param mediaType The media type, can  be {@code null}
     * @return True if is writable
     */
    default boolean isWriteable(@NonNull Argument<T> type, @Nullable MediaType mediaType) {
        return true;
    }

    /**
     * Prepare a {@link MessageBodyWriter} that will write the given type. This can be used for
     * precomputing some route data.
     *
     * @param type The type
     * @return The closure
     */
    default MessageBodyWriter<T> createSpecific(@NonNull Argument<T> type) {
        return this;
    }

    /**
     * {@code true} iff this closure can do a blocking <i>read</i> on the object it receives.
     * For example, if this closure writes from an {@code InputStream}, that operation may be
     * blocking and this method returns {@code true}.<br>
     * Note that even when this is {@code false},
     * {@link #writeTo(Argument, MediaType, Object, MutableHeaders, OutputStream)} may still block because the
     * {@link OutputStream} that is passed as the write destination may still block.
     *
     * @return Whether this writer may block
     */
    default boolean isBlocking() {
        return false;
    }

    /**
     * Resolve the charset.
     *
     * @param headers The headers
     * @return The charset
     */
    static @NonNull Charset getCharset(@NonNull Headers headers) {
        if (headers instanceof HttpHeaders httpHeaders) {
            Charset charset = httpHeaders.acceptCharset();
            if (charset != null) {
                return charset;
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Writes an object to the given output stream.
     *
     * @param type            The type
     * @param mediaType       The media type
     * @param object          The object to write
     * @param outgoingHeaders The HTTP headers
     * @param outputStream    The output stream
     * @throws CodecException If an error occurs decoding
     */
    void writeTo(
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        T object,
        @NonNull MutableHeaders outgoingHeaders,
        @NonNull OutputStream outputStream) throws CodecException;

    /**
     * Writes an object to the given stream.
     *
     * @param type            The type
     * @param mediaType       The media type
     * @param object          The object to write
     * @param outgoingHeaders The HTTP headers
     * @param bufferFactory   A byte buffer factory
     * @throws CodecException If an error occurs decoding
     * @return The encoded byte buffer
     */
    @NonNull
    default ByteBuffer<?> writeTo(
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        T object,
        @NonNull MutableHeaders outgoingHeaders,
        @NonNull ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        ByteBuffer<?> buffer = bufferFactory.buffer();
        try {
            writeTo(type, mediaType, object, outgoingHeaders, buffer.toOutputStream());
        } catch (Throwable t) {
            if (buffer instanceof ReferenceCounted rc) {
                rc.release();
            }
            throw t;
        }
        return buffer;
    }
}
