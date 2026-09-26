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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * {@link PieceWriter} that writes each piece with
 * {@link ResponseBodyWriter#writePiece(ByteBodyFactory, HttpRequest, HttpResponse, Argument, MediaType, Object)}
 * and puts the separator in front of the bytes of the piece.
 *
 * @param <T> The type of the pieces
 * @author Jonas Konrad
 * @since 5.3.0
 */
@Internal
final class DefaultPieceWriter<T> implements PieceWriter<T> {
    private final ResponseBodyWriter<T> writer;
    private final ByteBodyFactory bodyFactory;
    private final HttpRequest<?> request;
    private final HttpResponse<?> response;
    private final Argument<T> type;
    private final MediaType mediaType;

    DefaultPieceWriter(ResponseBodyWriter<T> writer, ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<T> type, MediaType mediaType) {
        this.writer = writer;
        this.bodyFactory = bodyFactory;
        this.request = request;
        this.response = response;
        this.type = type;
        this.mediaType = mediaType;
    }

    @Override
    public CloseableByteBody writePiece(@Nullable ReadBuffer separator, T object) throws CodecException {
        CloseableByteBody piece = writer.writePiece(bodyFactory, request, response, type, mediaType, object);
        return separator == null ? piece : prepend(bodyFactory, separator, piece);
    }

    /**
     * Put the given separator in front of the given piece.
     *
     * @param bodyFactory The body factory
     * @param separator   The separator, which is duplicated, not consumed
     * @param piece       The piece, which is consumed
     * @return The separator followed by the piece
     */
    static CloseableByteBody prepend(ByteBodyFactory bodyFactory, ReadBuffer separator, CloseableByteBody piece) {
        if (piece instanceof AvailableByteBody available) {
            return bodyFactory.adapt(bodyFactory.readBufferFactory().compose(List.of(separator.duplicate(), available.toReadBuffer())));
        }
        return ConcatenatingSubscriber.concatenate(bodyFactory, Flux.just(bodyFactory.adapt(separator.duplicate()), piece), ConcatenatingSubscriber.Separators.NONE);
    }

    @Override
    public void close() {
        // nothing is held between pieces
    }
}
