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

/**
 * {@link PieceWriter} for JSON. Keeps one {@link JsonStreamWriter} of the mapper open across the
 * pieces of a response, writing into a stream whose buffer is cut off after each piece and handed
 * downstream as the body of that piece. The separator in front of a piece goes into the same
 * buffer, so the bytes of a piece are never copied or composed.
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
     * The stream the JSON writer writes to for the whole response. The bytes go into a buffer of
     * the {@link ReadBufferFactory}, opened when the first byte of a piece arrives and cut off
     * when the piece is done, at which point it becomes the body of the piece.
     *
     * <p>Closing the stream does nothing: a mapper may close the stream it was given after every
     * value, as {@link JsonMapper#writeValue(OutputStream, Argument, Object)} implementations
     * commonly do, and the pieces after that must still be written. The piece writer discards the
     * stream itself when it is closed.
     */
    private static final class BufferStream extends OutputStream {
        private final ReadBufferFactory factory;
        private ReadBufferFactory.@Nullable BufferingOutputStream buffer;

        BufferStream(ReadBufferFactory factory) {
            this.factory = factory;
        }

        private OutputStream target() {
            ReadBufferFactory.BufferingOutputStream current = this.buffer;
            if (current == null) {
                current = factory.outputStreamBuffer();
                this.buffer = current;
            }
            return current.stream();
        }

        @Override
        public void write(int b) throws IOException {
            target().write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            target().write(b, off, len);
        }

        /**
         * Take the bytes written since the last cut as a buffer.
         *
         * @return The bytes
         * @throws IOException If the buffer cannot be finished
         */
        ReadBuffer cut() throws IOException {
            ReadBufferFactory.BufferingOutputStream current = this.buffer;
            if (current == null) {
                return factory.createEmpty();
            }
            this.buffer = null;
            return current.finishBuffer();
        }

        /**
         * Drop the bytes written since the last cut.
         */
        void discard() {
            ReadBufferFactory.BufferingOutputStream current = this.buffer;
            if (current != null) {
                this.buffer = null;
                try {
                    current.close();
                } catch (IOException e) {
                    // the buffer is released either way
                }
            }
        }

        @Override
        public void close() {
            // see the class javadoc
        }
    }
}
