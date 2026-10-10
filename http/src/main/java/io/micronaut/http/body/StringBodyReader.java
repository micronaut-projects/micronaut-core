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
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

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
        Charset charset = getCharset(mediaType);
        return Flux.defer(() -> {
            // A multi-byte character can span buffers, so all buffers share one decoder
            ChunkDecoder decoder = new ChunkDecoder(charset);
            return Flux.from(input)
                .map(decoder::decode)
                .concatWith(Mono.fromSupplier(decoder::finish))
                .filter(s -> !s.isEmpty());
        });
    }

    /**
     * Decodes consecutive buffers, carrying an incomplete trailing character over to the next one.
     */
    private static final class ChunkDecoder {
        private static final java.nio.ByteBuffer EMPTY = java.nio.ByteBuffer.allocate(0);

        private final CharsetDecoder decoder;
        private java.nio.ByteBuffer leftover = EMPTY;

        ChunkDecoder(Charset charset) {
            decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        String decode(ByteBuffer<?> byteBuffer) {
            try {
                return decode(java.nio.ByteBuffer.wrap(byteBuffer.toByteArray()), false);
            } finally {
                if (byteBuffer instanceof ReferenceCounted rc) {
                    rc.release();
                }
            }
        }

        String finish() {
            return decode(EMPTY, true);
        }

        private String decode(java.nio.ByteBuffer chunk, boolean endOfInput) {
            java.nio.ByteBuffer in = chunk;
            if (leftover.hasRemaining()) {
                in = java.nio.ByteBuffer.allocate(leftover.remaining() + chunk.remaining()).put(leftover).put(chunk).flip();
            }
            CharBuffer out = CharBuffer.allocate((int) Math.ceil(in.remaining() * (double) decoder.maxCharsPerByte()) + 2);
            decoder.decode(in, out, endOfInput);
            if (endOfInput) {
                decoder.flush(out);
            }
            leftover = in.hasRemaining() ? java.nio.ByteBuffer.allocate(in.remaining()).put(in).flip() : EMPTY;
            return out.flip().toString();
        }
    }

    /**
     * Only the charset of the {@code Content-Type} describes how the body is encoded. The
     * {@code Accept-Charset} header is a preference for the response and is not consulted.
     */
    private Charset getCharset(@Nullable MediaType mediaType) {
        return mediaType == null ? defaultCharset : mediaType.getCharset().orElse(defaultCharset);
    }
}
