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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.ArrayDeque;
import java.util.function.Function;

/**
 * {@link PieceReader} support: the publisher of the elements a piece reader reads, the piece
 * reader of a chunked reader that only reads a publisher, and the reader of one element per
 * piece.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class PieceReaders {

    private PieceReaders() {
    }

    /**
     * The elements a piece reader reads from the pieces of a body, as a publisher without
     * Reactor: an element is decoded when it is requested.
     *
     * @param input  The pieces of the body
     * @param reader The reader of the pieces, which the publisher takes over
     * @param <T>    The type of an element
     * @return The publisher of the elements, for one subscriber
     */
    public static <T> Publisher<T> publisher(Publisher<ReadBuffer> input, PieceReader<T> reader) {
        return new PieceReaderPublisher<>(input, Function.identity(), ReadBuffer::close, reader);
    }

    /**
     * The elements a piece reader reads from the buffers of a body, as a publisher without
     * Reactor.
     *
     * @param input   The buffers of the body
     * @param reader  The reader of the pieces, which the publisher takes over
     * @param adapter The read buffer of a buffer, which takes it over
     * @param <T>     The type of an element
     * @return The publisher of the elements, for one subscriber
     */
    public static <T> Publisher<T> publisherOfBuffers(Publisher<ByteBuffer<?>> input,
                                                      PieceReader<T> reader,
                                                      Function<ByteBuffer<?>, ReadBuffer> adapter) {
        return new PieceReaderPublisher<>(input, adapter, PieceReaders::release, reader);
    }

    /**
     * The read buffer of a buffer of the JDK factory: the bytes are copied unless the buffer is
     * backed by a NIO buffer.
     *
     * @param buffer The buffer, which the read buffer takes over
     * @return The read buffer
     */
    public static ReadBuffer adapt(ByteBuffer<?> buffer) {
        return ReadBufferFactory.getJdkFactory().adapt(buffer);
    }

    /**
     * The piece reader of a chunked reader: its own, or one over its publisher, which must emit
     * while it is fed.
     *
     * @param reader         The chunked reader
     * @param type           The type of an element
     * @param mediaType      The media type
     * @param headers        The headers
     * @param maxElementSize The maximum number of bytes of an element
     * @param <T>            The type of an element
     * @return The piece reader
     */
    public static <T> PieceReader<T> open(ChunkedMessageBodyReader<T> reader,
                                          Argument<T> type,
                                          @Nullable MediaType mediaType,
                                          Headers headers,
                                          long maxElementSize) {
        PieceReader<T> own = reader.openPieceReader(type, mediaType, headers, maxElementSize);
        if (own != null) {
            return own;
        }
        return new PublisherPieceReader<>(reader.getClass().getName(), input -> reader.readChunked(type, mediaType, headers, input, maxElementSize));
    }

    /**
     * A reader of one element per piece, decoded when it is polled.
     *
     * @param reader Decodes the buffer of a piece, which it takes over
     * @param <T>    The type of an element
     * @return The piece reader
     */
    public static <T> PieceReader<T> eachPiece(Function<ByteBuffer<?>, T> reader) {
        return new EachPieceReader<>(reader);
    }

    private static void release(ByteBuffer<?> buffer) {
        if (buffer instanceof ReferenceCounted counted) {
            counted.release();
        }
    }

    /**
     * One element per piece.
     *
     * @param <T> The type of an element
     */
    private static final class EachPieceReader<T> implements PieceReader<T> {
        private final Function<ByteBuffer<?>, T> reader;
        private final ArrayDeque<ReadBuffer> pieces = new ArrayDeque<>(1);
        private boolean closed;

        EachPieceReader(Function<ByteBuffer<?>, T> reader) {
            this.reader = reader;
        }

        @Override
        public void read(ReadBuffer piece) {
            if (closed) {
                piece.close();
            } else {
                pieces.add(piece);
            }
        }

        @Override
        public void complete() {
            // every piece is an element
        }

        @Override
        public @Nullable T poll() {
            ReadBuffer piece = pieces.poll();
            if (piece == null) {
                return null;
            }
            ByteBuffer<?> buffer;
            try (piece) {
                buffer = piece.toByteBuffer();
            }
            return reader.apply(buffer);
        }

        @Override
        public void close() {
            closed = true;
            ReadBuffer piece;
            while ((piece = pieces.poll()) != null) {
                piece.close();
            }
        }
    }
}
