/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.compression;

import io.micronaut.core.annotation.Experimental;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.BrotliDecoder;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.compression.ZstdDecoder;
import io.netty.handler.codec.compression.ZstdEncoder;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import org.jspecify.annotations.Nullable;

/**
 * Creates compression handlers for HTTP connections, streams, and WebSocket extensions.
 * Calls may arrive concurrently from different event loops. Each call must return a fresh
 * handler whose resources are released when it is removed, except for sharable WebSocket handlers.
 * A replacement bean applies independently of the configured channel transport.
 *
 * @since 5.3.0
 */
@Experimental
public interface NettyZlibCodecFactory {
    /**
     * Creates an encoder. {@link ZlibWrapper#ZLIB_OR_NONE} is not valid for encoding.
     *
     * @param wrapper The wrapper format
     * @param compressionLevel The compression level, from 0 to 9
     * @param windowBits The window size, from 9 to 15
     * @param memLevel The memory level, from 1 to 9
     * @return A fresh encoder
     * @since 5.3.0
     */
    ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel);

    /**
     * Creates a decoder, including the zlib-or-raw compatibility mode.
     *
     * @param wrapper The wrapper format
     * @param maxAllocation The maximum output buffer allocation, or 0 for no allocation limit
     * @return A fresh decoder
     * @since 5.3.0
     */
    ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation);

    /**
     * Compresses a complete body into one zlib or gzip stream, in place of an encoder from
     * {@link #newZlibEncoder} when the caller already has the whole body. The result must decode
     * to the body, but need not match the encoder's bytes. The default returns {@code null}, so the
     * caller streams the body through an encoder.
     *
     * @param wrapper The wrapper format, {@link ZlibWrapper#GZIP} or {@link ZlibWrapper#ZLIB}
     * @param compressionLevel The compression level, from 0 to 9
     * @param windowBits The window size, from 9 to 15
     * @param memLevel The memory level, from 1 to 9
     * @param body The complete body, still owned by the caller
     * @param allocator The allocator for the result
     * @return The compressed stream, owned by the caller, or {@code null} to use an encoder
     * @since 5.3.0
     */
    default @Nullable ByteBuf compressZlib(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel,
                                          ByteBuf body, ByteBufAllocator allocator) {
        return null;
    }

    /**
     * Creates a server handler that negotiates WebSocket compression extensions. The default
     * retains Netty's compression settings and allocation policy. Overrides may enforce the
     * endpoint's payload limit on decompressed messages.
     *
     * @param maxMessageBytes The endpoint's configured maximum message payload length
     * @return A fresh extension handler, or a sharable handler
     * @since 5.3.0
     */
    default WebSocketServerExtensionHandler newWebSocketServerCompressionHandler(int maxMessageBytes) {
        return new WebSocketServerCompressionHandler();
    }

    /**
     * Creates a client handler that negotiates WebSocket compression extensions. The default
     * retains Netty's compression settings and allocation policy and returns its sharable singleton.
     * Overrides may enforce the endpoint's payload limit on decompressed messages.
     *
     * @param maxMessageBytes The endpoint's configured maximum message payload length
     * @return A fresh extension handler, or a sharable handler
     * @since 5.3.0
     */
    default WebSocketClientExtensionHandler newWebSocketClientCompressionHandler(int maxMessageBytes) {
        return WebSocketClientCompressionHandler.INSTANCE;
    }

    /**
     * Reports whether this provider supports Brotli encoding and decoding. Overrides need not
     * initialize Netty's optional Brotli implementation.
     *
     * @return Whether Brotli is available
     * @since 5.3.0
     */
    default boolean isBrotliAvailable() {
        return Brotli.isAvailable();
    }

    /**
     * Reports whether this provider supports Zstandard encoding and decoding.
     *
     * @return Whether Zstandard is available
     * @since 5.3.0
     */
    default boolean isZstdAvailable() {
        return Zstd.isAvailable();
    }

    /**
     * Creates a Brotli encoder with Netty's default quality (4) and text mode.
     * Called only when {@link #isBrotliAvailable()} is true.
     *
     * @return A fresh encoder
     * @since 5.3.0
     */
    default ChannelHandler newBrotliEncoder() {
        return new BrotliEncoder();
    }

    /**
     * Creates a Brotli decoder. Called only when {@link #isBrotliAvailable()} is true.
     *
     * @param maxAllocation The maximum output buffer allocation, or 0 for no allocation limit
     * @return A fresh decoder
     * @since 5.3.0
     */
    default ChannelHandler newBrotliDecoder(int maxAllocation) {
        return BrotliDecoder.newDecoderWithMaxAllocation(maxAllocation);
    }

    /**
     * Creates a Zstandard encoder with Netty's default block size.
     * Called only when {@link #isZstdAvailable()} is true.
     *
     * @param compressionLevel The configured compression level
     * @param maxEncodeSize The maximum size of data to encode in one operation
     * @return A fresh encoder
     * @since 5.3.0
     */
    default ChannelHandler newZstdEncoder(int compressionLevel, int maxEncodeSize) {
        return new ZstdEncoder(compressionLevel, StandardCompressionOptions.zstd().blockSize(), maxEncodeSize);
    }

    /**
     * Creates a Zstandard decoder. Called only when {@link #isZstdAvailable()} is true.
     *
     * @param maxAllocation The maximum output buffer allocation, or 0 for no allocation limit
     * @return A fresh decoder
     * @since 5.3.0
     */
    default ChannelHandler newZstdDecoder(int maxAllocation) {
        return new ZstdDecoder(maxAllocation);
    }

    /**
     * Creates a Snappy encoder using the framed stream format, including chunk checksums.
     *
     * @return A fresh encoder
     * @since 5.3.0
     */
    default ChannelHandler newSnappyFrameEncoder() {
        return new SnappyFrameEncoder();
    }

    /**
     * Creates a framed Snappy decoder with Netty's default checksum policy (no validation).
     *
     * @return A fresh decoder
     * @since 5.3.0
     */
    default ChannelHandler newSnappyFrameDecoder() {
        return new SnappyFrameDecoder();
    }
}
