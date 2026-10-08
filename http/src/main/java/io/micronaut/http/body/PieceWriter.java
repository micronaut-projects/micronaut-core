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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.codec.CodecException;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;

/**
 * Writer for the pieces of one streamed response, such as the elements of a
 * {@link org.reactivestreams.Publisher} body. Opened by
 * {@link ResponseBodyWriter#openPieceWriter}, it may keep state across the pieces, e.g. the
 * generator of a JSON mapper, where
 * {@link ResponseBodyWriter#writePiece(ByteBodyFactory, io.micronaut.http.HttpRequest, io.micronaut.http.HttpResponse, io.micronaut.core.type.Argument, io.micronaut.http.MediaType, Object)}
 * sets that state up for every piece.
 *
 * <p>The pieces are written one at a time; {@link #writePiece} and {@link #close()} are never
 * called concurrently.
 *
 * @param <T> The type of the pieces
 * @author Jonas Konrad
 * @since 5.3.0
 */
@Experimental
public interface PieceWriter<T> extends Closeable {
    /**
     * Write one piece, optionally preceded by a separator. The separator goes into the same body
     * as the piece, so that one piece leads to one body downstream (and thus, for a netty
     * response, one HTTP chunk and one flush).
     *
     * @param separator The bytes to write in front of the piece, or {@code null} for none. The
     *                  caller keeps ownership of this buffer and may pass the same buffer for
     *                  many pieces, so the writer must {@link ReadBuffer#duplicate() duplicate}
     *                  it before consuming it
     * @param object    The piece to write
     * @return The bytes of the separator and the piece
     * @throws CodecException If an error occurs encoding
     */
    CloseableByteBody writePiece(@Nullable ReadBuffer separator, T object) throws CodecException;

    /**
     * Close this writer, releasing any state kept across the pieces. No piece is written after
     * this. May be called more than once.
     */
    @Override
    void close();
}
