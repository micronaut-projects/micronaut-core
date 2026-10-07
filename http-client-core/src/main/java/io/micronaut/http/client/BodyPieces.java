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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.Function;

/**
 * The pieces of a response body as they were received, as heap buffers that are not reference
 * counted. Empty pieces are skipped.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class BodyPieces implements PieceReader<ByteBuffer<?>> {

    private final ArrayDeque<ByteBuffer<?>> pieces = new ArrayDeque<>(1);

    private BodyPieces() {
    }

    /**
     * @param body The body, which the elements take over
     * @return The pieces of the body
     */
    @SuppressWarnings("java:S2095") // the elements own the piece reader, and close it
    public static BodyElements<ByteBuffer<?>> elements(CloseableByteBody body) {
        return new ByteBodyElements<>(body, new BodyPieces(), BodyPieces::wrap);
    }

    /**
     * @return The reader of the pieces of a body: every piece that is not empty is an element, a
     * byte array buffer
     */
    static PieceReader<ByteBuffer<?>> reader() {
        return new BodyPieces();
    }

    /**
     * The lines of a response body, without their line ending: a line feed, a carriage return, or
     * a carriage return and a line feed. Every line is an element, an empty one too, and the bytes after the last
     * line ending are not, as the lines of an event stream were split before.
     *
     * @param body          The body, which the elements take over
     * @param maxLineLength The largest number of bytes a line may have
     * @return The lines
     */
    public static BodyElements<ByteBuffer<?>> lines(CloseableByteBody body, long maxLineLength) {
        return new ByteBodyElements<>(body, lineReader(maxLineLength, ByteArrayBufferFactory.INSTANCE::wrap), BodyPieces::wrap);
    }

    /**
     * The reader of the lines of a body, without their line ending: a line feed, a carriage
     * return, or a carriage return and a line feed, as for an event stream. Every line is an
     * element, an empty one too, and the bytes after the last line ending are not.
     *
     * @param maxLineLength The largest number of bytes a line may have
     * @param factory       The element of the bytes of a line
     * @param <T>           The type of a line
     * @return The reader
     */
    public static <T> PieceReader<T> lineReader(long maxLineLength, Function<byte[], T> factory) {
        return new Lines<>(maxLineLength, factory);
    }

    /**
     * The failure of the elements of a response body, an {@link HttpClientException}.
     *
     * @param error A failure to read the body
     * @return The failure of the elements
     */
    public static Throwable wrap(Throwable error) {
        return error instanceof HttpClientException ? error : new HttpClientException("Error reading the response body: " + error.getMessage(), error);
    }

    @Override
    public void read(ReadBuffer piece) {
        try (piece) {
            if (piece.readable() > 0) {
                pieces.add(ByteArrayBufferFactory.INSTANCE.wrap(piece.toArray()));
            }
        }
    }

    @Override
    public void complete() {
        // every piece is an element
    }

    @Override
    public @Nullable ByteBuffer<?> poll() {
        return pieces.poll();
    }

    @Override
    public void close() {
        pieces.clear();
    }

    /**
     * Splits the pieces of a body into lines. A line ends with a line feed, a carriage return,
     * or a carriage return and a line feed, as the lines of an event stream do. The bytes of a
     * line that is not ended yet are kept in one growable array, so a long line that arrives in
     * many pieces is copied once per piece, not once per piece for every piece before it.
     *
     * @param <T> The type of a line
     */
    private static final class Lines<T> implements PieceReader<T> {
        private final long maxLineLength;
        private final Function<byte[], T> factory;
        private final ArrayDeque<byte[]> lines = new ArrayDeque<>(1);
        /**
         * The bytes of the line that is not ended yet: the first {@link #pendingLength}.
         */
        private byte[] pending = new byte[0];
        private int pendingLength;
        /**
         * The last byte read was a carriage return: a line feed that follows it is part of the
         * same line ending.
         */
        private boolean afterCr;

        Lines(long maxLineLength, Function<byte[], T> factory) {
            this.maxLineLength = maxLineLength;
            this.factory = factory;
        }

        @Override
        public void read(ReadBuffer piece) {
            byte[] bytes;
            try (piece) {
                bytes = piece.toArray();
            }
            int start = 0;
            for (int i = 0; i < bytes.length; i++) {
                byte b = bytes[i];
                if (b == '\n' && afterCr && i == start) {
                    // the line feed of a carriage return and a line feed
                    afterCr = false;
                    start = i + 1;
                    continue;
                }
                afterCr = false;
                if (b == '\n' || b == '\r') {
                    append(bytes, start, i);
                    lines.add(Arrays.copyOf(pending, pendingLength));
                    pendingLength = 0;
                    afterCr = b == '\r';
                    start = i + 1;
                }
            }
            append(bytes, start, bytes.length);
        }

        /**
         * Append bytes to the line that is not ended yet.
         */
        private void append(byte[] bytes, int from, int to) {
            int count = to - from;
            long length = (long) pendingLength + count;
            if (length > maxLineLength) {
                throw new ContentLengthExceededException(maxLineLength, length);
            }
            if (count == 0) {
                return;
            }
            if (length > pending.length) {
                pending = Arrays.copyOf(pending, (int) Math.min(Math.max(length, (long) pending.length * 2), Integer.MAX_VALUE - 8L));
            }
            System.arraycopy(bytes, from, pending, pendingLength, count);
            pendingLength = (int) length;
        }

        @Override
        public void complete() {
            // the bytes after the last line ending are not a line
            pendingLength = 0;
            pending = new byte[0];
        }

        @Override
        public @Nullable T poll() {
            byte[] line = lines.poll();
            return line == null ? null : factory.apply(line);
        }

        @Override
        public void close() {
            lines.clear();
            pendingLength = 0;
            pending = new byte[0];
        }
    }
}
