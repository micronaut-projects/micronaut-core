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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

/**
 * Variant of {@link MessageBodyReader} that allows piecewise reading of the input, e.g. for
 * json-stream.
 *
 * @param <T> The type to read
 */
@Experimental
public interface ChunkedMessageBodyReader<T> extends MessageBodyReader<T> {
    Publisher<? extends T> readChunked(
        Argument<T> type,
        @Nullable MediaType mediaType,
        Headers httpHeaders,
        Publisher<ByteBuffer<?>> input
    );

    /**
     * Read the input piecewise, like {@link #readChunked(Argument, MediaType, Headers, Publisher)},
     * with a limit of the bytes of each piece: a reader that buffers a piece to decode it, e.g. a
     * JSON value, fails the publisher with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when a piece exceeds
     * the limit. The body as a whole is not limited.
     *
     * <p>Each piece is read as the given type, a collection type too: a reader of JSON reads each
     * element of a top-level array, or each value of a JSON stream, as one piece, e.g.
     * {@code [[1,2],[3,4]]} as two lists of a {@code List<Integer>} type. This differs from
     * {@link #readChunked(Argument, MediaType, Headers, Publisher)}, which reads a JSON array
     * as one piece of a collection type.</p>
     *
     * <p>The default implementation ignores the limit, for a reader that does not buffer the
     * input to decode it.</p>
     *
     * @param type            The type of a piece
     * @param mediaType       The media type
     * @param httpHeaders     The headers
     * @param input           The input
     * @param maxElementSize  The maximum number of bytes of a piece
     * @return The pieces
     * @since 5.3.0
     */
    default Publisher<? extends T> readChunked(
        Argument<T> type,
        @Nullable MediaType mediaType,
        Headers httpHeaders,
        Publisher<ByteBuffer<?>> input,
        long maxElementSize
    ) {
        return readChunked(type, mediaType, httpHeaders, input);
    }

    /**
     * Open a reader of the pieces of one body that is fed the bytes of the body as they arrive,
     * without Reactive Streams: like
     * {@link #readChunked(Argument, MediaType, Headers, Publisher, long)}, each element of a
     * top-level JSON array, or each value of a JSON stream, is one piece, limited to the given
     * number of bytes.
     *
     * <p>The default implementation returns {@code null}: the reader only reads a publisher.</p>
     *
     * @param type           The type of a piece
     * @param mediaType      The media type
     * @param httpHeaders    The headers
     * @param maxElementSize The maximum number of bytes of a piece
     * @return The reader, or {@code null} if this reader does not read pieces without a publisher
     * @since 5.3.0
     */
    default @Nullable PieceReader<T> openPieceReader(
        Argument<T> type,
        @Nullable MediaType mediaType,
        Headers httpHeaders,
        long maxElementSize
    ) {
        return null;
    }
}
