/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.stream.PieceReaders;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

/**
 * The body reader for {@link String}.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Internal
@Singleton
@BootstrapContextCompatible
public final class StringBodyReader implements TypedMessageBodyReader<String>, ChunkedMessageBodyReader<String> {
    private final Charset defaultCharset;

    StringBodyReader(ApplicationConfiguration applicationConfiguration) {
        this.defaultCharset = applicationConfiguration.getDefaultCharset();
    }

    @Override
    public Argument<String> getType() {
        return Argument.STRING;
    }

    @Override
    public String read(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        return read0(byteBuffer, getCharset(mediaType));
    }

    private String read0(ByteBuffer<?> byteBuffer, Charset charset) {
        String s = byteBuffer.toString(charset);
        if (byteBuffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        return s;
    }

    @Override
    public String read(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        try {
            return new String(inputStream.readAllBytes(), getCharset(mediaType));
        } catch (IOException e) {
            throw new CodecException("Failed to read InputStream", e);
        }
    }

    @Override
    public Publisher<String> readChunked(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        Charset charset;
        try {
            charset = getCharset(mediaType);
        } catch (CodecException e) {
            // signalled through the publisher, which takes over the input and releases it: the
            // input has no other owner once it is passed here
            return PieceReaders.publisherOfBuffers(input, new FailingPieceReader(e), PieceReaders::adapt);
        }
        return PieceReaders.publisherOfBuffers(input, new DecodingPieceReader(charset), PieceReaders::adapt);
    }

    @Override
    public PieceReader<String> openPieceReader(Argument<String> type, @Nullable MediaType mediaType, Headers httpHeaders, long maxElementSize) {
        return new DecodingPieceReader(getCharset(mediaType));
    }

    /**
     * Only the charset of the {@code Content-Type} describes how the body is encoded. The
     * {@code Accept-Charset} header is a preference for the response and is not consulted.
     */
    private Charset getCharset(@Nullable MediaType mediaType) {
        if (mediaType == null) {
            return defaultCharset;
        }
        try {
            return mediaType.getCharset().orElse(defaultCharset);
        } catch (IllegalArgumentException e) {
            // IllegalCharsetNameException or UnsupportedCharsetException: the client sent a bad charset
            throw new CodecException("Unsupported charset in content type: " + mediaType, e);
        }
    }

    /**
     * Decodes consecutive pieces with one decoder: a multi-byte character can span pieces, so an
     * incomplete trailing character is carried over to the next piece.
     */
    private static final class DecodingPieceReader implements PieceReader<String> {
        private static final java.nio.ByteBuffer EMPTY = java.nio.ByteBuffer.allocate(0);

        private final Charset charset;
        private final boolean utf8;
        private final boolean singleByte;
        private @Nullable CharsetDecoder decoder;
        private @Nullable CharBuffer reusableOut;
        private final ArrayDeque<String> decoded = new ArrayDeque<>(1);
        private java.nio.ByteBuffer leftover = EMPTY;
        private boolean closed;

        DecodingPieceReader(Charset charset) {
            this.charset = charset;
            this.utf8 = charset.equals(StandardCharsets.UTF_8);
            this.singleByte = charset.equals(StandardCharsets.US_ASCII) || charset.equals(StandardCharsets.ISO_8859_1);
        }

        private CharsetDecoder decoder() {
            CharsetDecoder d = decoder;
            if (d == null) {
                d = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
                decoder = d;
            }
            return d;
        }

        @Override
        public void read(ReadBuffer piece) {
            if (closed) {
                piece.close();
                return;
            }
            if (piece.useFastHeapBuffer(this::decodeHeapBuffer) != null) {
                return;
            }
            byte[] bytes;
            try (piece) {
                bytes = piece.toArray();
            }
            decodeBytes(bytes, 0, bytes.length);
        }

        private Boolean decodeHeapBuffer(java.nio.ByteBuffer bb) {
            decodeBytes(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining());
            return Boolean.TRUE;
        }

        private void decodeBytes(byte[] bytes, int offset, int length) {
            if (length == 0) {
                return;
            }
            if (!leftover.hasRemaining() && (singleByte || (utf8 && (bytes[offset + length - 1] & 0x80) == 0))) {
                decoded.add(new String(bytes, offset, length, charset));
                return;
            }
            decode(java.nio.ByteBuffer.wrap(bytes, offset, length), false);
        }

        @Override
        public void complete() {
            if (!closed && (leftover.hasRemaining() || decoder != null)) {
                decode(EMPTY, true);
            }
        }

        @Override
        public @Nullable String poll() {
            return decoded.poll();
        }

        /**
         * The pieces are decoded as they are read, so no string is pending when none is decoded:
         * the end of the input is signalled without another request.
         */
        @Override
        public boolean isEmpty() {
            return decoded.isEmpty();
        }

        @Override
        public void close() {
            closed = true;
            decoded.clear();
            leftover = EMPTY;
            reusableOut = null;
        }

        private void decode(java.nio.ByteBuffer chunk, boolean endOfInput) {
            java.nio.ByteBuffer in = chunk;
            if (leftover.hasRemaining()) {
                in = java.nio.ByteBuffer.allocate(leftover.remaining() + chunk.remaining()).put(leftover).put(chunk).flip();
            }
            CharsetDecoder d = decoder();
            int needed = (int) Math.ceil(in.remaining() * (double) d.maxCharsPerByte()) + 2;
            CharBuffer out = reusableOut;
            if (out == null || out.capacity() < needed) {
                out = CharBuffer.allocate(needed);
                reusableOut = out;
            } else {
                out.clear();
            }
            d.decode(in, out, endOfInput);
            if (endOfInput) {
                d.flush(out);
            }
            leftover = in.hasRemaining() ? java.nio.ByteBuffer.allocate(in.remaining()).put(in).flip() : EMPTY;
            if (out.position() > 0) {
                decoded.add(out.flip().toString());
            }
        }
    }

    /**
     * Fails the read of a body that cannot be decoded, e.g. because of an unsupported charset,
     * closing the pieces it is given.
     */
    private static final class FailingPieceReader implements PieceReader<String> {
        private final CodecException failure;

        FailingPieceReader(CodecException failure) {
            this.failure = failure;
        }

        @Override
        public void read(ReadBuffer piece) {
            piece.close();
            throw failure;
        }

        @Override
        public void complete() {
            throw failure;
        }

        @Override
        public @Nullable String poll() {
            return null;
        }

        @Override
        public void close() {
            // nothing is buffered
        }
    }
}
