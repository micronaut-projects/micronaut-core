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
     * The lines of a response body, without their line ending: a line feed, or a carriage return
     * and a line feed. Every line is an element, an empty one too, and the bytes after the last
     * line ending are not, as the lines of an event stream were split before.
     *
     * @param body          The body, which the elements take over
     * @param maxLineLength The largest number of bytes a line may have
     * @return The lines
     */
    public static BodyElements<ByteBuffer<?>> lines(CloseableByteBody body, long maxLineLength) {
        return new ByteBodyElements<>(body, new Lines(maxLineLength), BodyPieces::wrap);
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
     * Splits the pieces of a body into lines.
     */
    private static final class Lines implements PieceReader<ByteBuffer<?>> {
        private final long maxLineLength;
        private final ArrayDeque<ByteBuffer<?>> lines = new ArrayDeque<>(1);
        /**
         * The bytes of the line that is not ended yet.
         */
        private byte[] pending = new byte[0];

        Lines(long maxLineLength) {
            this.maxLineLength = maxLineLength;
        }

        @Override
        public void read(ReadBuffer piece) {
            byte[] bytes;
            try (piece) {
                bytes = piece.toArray();
            }
            int start = 0;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\n') {
                    byte[] line = join(bytes, start, i);
                    int length = line.length;
                    if (length > 0 && line[length - 1] == '\r') {
                        length--;
                    }
                    lines.add(ByteArrayBufferFactory.INSTANCE.wrap(length == line.length ? line : Arrays.copyOf(line, length)));
                    pending = new byte[0];
                    start = i + 1;
                }
            }
            pending = join(bytes, start, bytes.length);
        }

        /**
         * @return The pending bytes and the given ones
         */
        private byte[] join(byte[] bytes, int from, int to) {
            long length = (long) pending.length + (to - from);
            if (length > maxLineLength) {
                throw new ContentLengthExceededException(maxLineLength, length);
            }
            byte[] joined = Arrays.copyOf(pending, (int) length);
            System.arraycopy(bytes, from, joined, pending.length, to - from);
            return joined;
        }

        @Override
        public void complete() {
            // the bytes after the last line ending are not a line
            pending = new byte[0];
        }

        @Override
        public @Nullable ByteBuffer<?> poll() {
            return lines.poll();
        }

        @Override
        public void close() {
            lines.clear();
            pending = new byte[0];
        }
    }
}
