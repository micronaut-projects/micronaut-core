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
package io.micronaut.json.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.PieceWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonStreamWriter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * {@link PieceWriter} for JSON. Keeps one {@link JsonStreamWriter} of the mapper open across the
 * pieces of a response, writing into a stream whose bytes are cut off after each piece and handed
 * downstream as the body of that piece. The separator in front of a piece goes into the same
 * buffer, so a piece is never composed of several buffers.
 *
 * @param <T> The type of the pieces
 * @author Jonas Konrad
 * @since 5.3.0
 */
@Internal
final class JsonPieceWriter<T> implements PieceWriter<T> {
    private final ByteBodyFactory bodyFactory;
    private final BufferStream stream;
    private final JsonStreamWriter<T> writer;

    /**
     * The separators of a response are a few shared buffers, so the bytes of the one seen last
     * are kept instead of being read out of the buffer for every piece.
     */
    private @Nullable ReadBuffer lastSeparator;
    private byte @Nullable [] lastSeparatorBytes;

    JsonPieceWriter(ByteBodyFactory bodyFactory, JsonMapper jsonMapper, Argument<T> type) throws CodecException {
        this.bodyFactory = bodyFactory;
        this.stream = new BufferStream(bodyFactory.readBufferFactory());
        try {
            this.writer = jsonMapper.createStreamWriter(stream, type);
        } catch (IOException | RuntimeException e) {
            throw new CodecException("Error creating JSON writer for type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public CloseableByteBody writePiece(@Nullable ReadBuffer separator, T object) throws CodecException {
        try {
            if (separator != null) {
                stream.write(separatorBytes(separator));
            }
            writer.write(object);
            return bodyFactory.adapt(stream.cut());
        } catch (IOException | RuntimeException e) {
            // the response fails with this piece, so what was written of it is dropped
            stream.discard();
            throw JsonMessageHandler.decorateWrite(object, e);
        }
    }

    private byte[] separatorBytes(ReadBuffer separator) {
        byte[] bytes = lastSeparatorBytes;
        if (bytes == null || separator != lastSeparator) {
            bytes = separator.duplicate().toArray();
            lastSeparator = separator;
            lastSeparatorBytes = bytes;
        }
        return bytes;
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            // nothing is written on close, and the stream cannot fail to close
        } finally {
            // drops any bytes the writer wrote to finish up
            stream.discard();
        }
    }

    /**
     * The stream the JSON writer writes to for the whole response. The bytes of a piece go into a
     * heap array that is kept across the pieces. A small piece is copied out at its exact size,
     * which is cheaper than taking a buffer of the {@link ReadBufferFactory} for every piece: the
     * transport combines adjacent small pieces anyway. A larger piece is copied into a buffer of
     * the factory, so it reaches the transport without leaving garbage behind, and the array is
     * kept for the next piece.
     *
     * <p>Closing the stream does nothing: a mapper may close the stream it was given after every
     * value, as {@link JsonMapper#writeValue(OutputStream, Argument, Object)} implementations
     * commonly do, and the pieces after that must still be written. The piece writer discards the
     * stream itself when it is closed.
     */
    private static final class BufferStream extends OutputStream {
        private static final int INITIAL_CAPACITY = 512;
        private static final int SMALL_PIECE_LIMIT = 1024;
        private static final int MAX_RETAINED_CAPACITY = 64 * 1024;

        private final ReadBufferFactory factory;
        private byte[] scratch = new byte[INITIAL_CAPACITY];
        private int count;

        BufferStream(ReadBufferFactory factory) {
            this.factory = factory;
        }

        private void ensureCapacity(int extra) {
            int needed = count + extra;
            if (needed < 0) {
                throw new OutOfMemoryError("Piece too large");
            }
            if (needed > scratch.length) {
                scratch = Arrays.copyOf(scratch, Math.max(needed, scratch.length << 1));
            }
        }

        @Override
        public void write(int b) {
            ensureCapacity(1);
            scratch[count++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            ensureCapacity(len);
            System.arraycopy(b, off, scratch, count, len);
            count += len;
        }

        /**
         * Take the bytes written since the last cut as a buffer.
         *
         * @return The bytes
         */
        ReadBuffer cut() {
            int n = count;
            if (n == 0) {
                return factory.createEmpty();
            }
            count = 0;
            byte[] current = scratch;
            ReadBuffer piece;
            if (n <= SMALL_PIECE_LIMIT) {
                // the transport combines adjacent small pieces, so an exact heap copy is cheapest
                piece = factory.adapt(Arrays.copyOf(current, n));
            } else {
                // a larger piece goes out on its own: copy it into a buffer of the factory, which
                // the transport writes without another copy, and keep the array for the next piece
                piece = factory.copyOf(ByteBuffer.wrap(current, 0, n));
            }
            if (current.length > MAX_RETAINED_CAPACITY) {
                // do not keep an array that one very large piece grew for the pieces after it
                scratch = new byte[INITIAL_CAPACITY];
            }
            return piece;
        }

        /**
         * Drop the bytes written since the last cut.
         */
        void discard() {
            count = 0;
        }

        @Override
        public void close() {
            // see the class javadoc
        }
    }
}
