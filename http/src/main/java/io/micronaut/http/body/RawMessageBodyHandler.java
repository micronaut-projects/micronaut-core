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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

import java.util.Collection;

/**
 * A body handler that is responsible for "raw" writing/reading, i.e. without a format like JSON.
 * These handlers do not care about the media type, they assume the user is passing in pre-formatted
 * data.
 *
 * @param <T> The raw message type
 */
@Internal
@Experimental
public interface RawMessageBodyHandler<T> extends MessageBodyHandler<T>, ChunkedMessageBodyReader<T> {
    /**
     * Supported types of this raw body handler. Exact match is used for reading. For writing, the
     * match is covariant. For example, if this returns {@code [String, CharSequence]}, then this
     * raw handler will be used for reading types declared as exactly {@code String} or
     * {@code CharSequence}, and will additionally be used for writing (but not reading) subtypes
     * (e.g. {@code StringBuilder}).
     *
     * @return The supported types
     */
    @NonNull
    Collection<? extends Class<?>> getTypes();

    /**
     * Same as {@link #writeTo(Argument, MediaType, Object, MutableHeaders, ByteBufferFactory)} but
     * with fewer parameters.
     *
     * @param mediaType       The media type
     * @param object          The object to write
     * @param bufferFactory   A byte buffer factory
     * @throws CodecException If an error occurs decoding
     * @return The encoded byte buffer
     */
    ByteBuffer<?> writeTo(MediaType mediaType, T object, ByteBufferFactory<?, ?> bufferFactory) throws CodecException;

    @Override
    default ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        if (mediaType != null && !outgoingHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType);
        }
        return writeTo(mediaType, object, bufferFactory);
    }
}
