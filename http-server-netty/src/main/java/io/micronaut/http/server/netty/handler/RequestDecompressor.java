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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecompressor;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.compression.JdkZlibDecompressor;
import io.netty.handler.codec.compression.SnappyFrameDecompressor;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdDecompressor;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Decompresses a request body with a {@link Decompressor}, handing the decompressed data to an
 * output callback. The callback may {@link #close()} this decompressor, e.g. when the body
 * exceeds its size limit, which stops the decompression.
 *
 * @since 5.3.0
 */
@Internal
final class RequestDecompressor {
    /**
     * Maximum size of a decompressed buffer. Brotli and snappy produce chunks of this size too.
     */
    private static final int MAX_OUTPUT_CHUNK_SIZE = 64 * 1024;
    /**
     * RFC 8878 §3.1.1.1.2 limits the window of the zstd content coding to 8 MiB.
     */
    private static final int ZSTD_MAX_WINDOW_LOG = 23;

    private final Decompressor decompressor;
    private boolean closed;
    private boolean receivedInput;

    private RequestDecompressor(Decompressor decompressor) {
        this.decompressor = decompressor;
    }

    /**
     * Create a decompressor for the given content encoding.
     *
     * @param allocator       The allocator for the decompressed buffers
     * @param contentEncoding The content encoding of the request
     * @return The decompressor, or {@code null} if the encoding is {@code identity} or unsupported
     */
    static @Nullable RequestDecompressor create(ByteBufAllocator allocator, CharSequence contentEncoding) {
        Decompressor decompressor;
        if (HttpHeaderValues.GZIP.contentEqualsIgnoreCase(contentEncoding) ||
            HttpHeaderValues.X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
            decompressor = JdkZlibDecompressor.builder()
                .wrapper(ZlibWrapper.GZIP)
                .decompressConcatenated(true)
                .maxAllocation(MAX_OUTPUT_CHUNK_SIZE)
                .build(allocator);
        } else if (HttpHeaderValues.DEFLATE.contentEqualsIgnoreCase(contentEncoding) ||
            HttpHeaderValues.X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
            // To be strict, 'deflate' means ZLIB, but some clients send raw deflate
            decompressor = JdkZlibDecompressor.builder()
                .wrapper(ZlibWrapper.ZLIB_OR_NONE)
                .maxAllocation(MAX_OUTPUT_CHUNK_SIZE)
                .build(allocator);
        } else if (Brotli.isAvailable() && HttpHeaderValues.BR.contentEqualsIgnoreCase(contentEncoding)) {
            decompressor = BrotliDecompressor.builder().maxOutputChunkSize(MAX_OUTPUT_CHUNK_SIZE).build(allocator);
        } else if (Zstd.isAvailable() && HttpHeaderValues.ZSTD.contentEqualsIgnoreCase(contentEncoding)) {
            decompressor = ZstdDecompressor.builder().maxWindowLog(ZSTD_MAX_WINDOW_LOG).build(allocator);
        } else if (HttpHeaderValues.SNAPPY.contentEqualsIgnoreCase(contentEncoding)) {
            decompressor = SnappyFrameDecompressor.builder().build(allocator);
        } else {
            return null;
        }
        return new RequestDecompressor(decompressor);
    }

    /**
     * Decompress some data. Data after the end of the compressed stream is ignored.
     *
     * @param compressed The compressed data. Release ownership is transferred to this method
     * @param output     The callback for the decompressed data, which takes release ownership
     * @throws DecompressionException If the data cannot be decompressed. This decompressor is
     * closed then
     */
    void add(ByteBuf compressed, Consumer<ByteBuf> output) throws DecompressionException {
        if (closed || !compressed.isReadable()) {
            compressed.release();
            return;
        }
        receivedInput = true;
        try {
            if (decompressor.status() == Decompressor.Status.COMPLETE) {
                compressed.release();
                return;
            }
            decompressor.addInput(compressed);
            forwardOutput(output);
        } catch (DecompressionException e) {
            close();
            throw e;
        }
    }

    /**
     * Signal the end of the compressed data, and close this decompressor. An empty body is
     * accepted as is, even though it is not a valid compressed stream.
     *
     * @param output The callback for the decompressed data, which takes release ownership
     * @throws DecompressionException If the compressed data is truncated. This decompressor is
     * closed then
     */
    void finish(Consumer<ByteBuf> output) throws DecompressionException {
        if (closed) {
            return;
        }
        try {
            if (receivedInput && decompressor.status() == Decompressor.Status.NEED_INPUT) {
                decompressor.endOfInput();
                forwardOutput(output);
            }
        } finally {
            close();
        }
    }

    private void forwardOutput(Consumer<ByteBuf> output) {
        while (!closed && decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
            ByteBuf decompressed = decompressor.takeOutput();
            if (decompressed.isReadable()) {
                output.accept(decompressed);
            } else {
                decompressed.release();
            }
        }
    }

    /**
     * Release the resources of this decompressor. This method is idempotent.
     */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            decompressor.close();
        } catch (DecompressionException ignored) {
        }
    }
}
