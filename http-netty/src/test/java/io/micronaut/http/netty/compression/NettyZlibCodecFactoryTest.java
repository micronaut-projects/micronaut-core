package io.micronaut.http.netty.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import io.netty.handler.codec.compression.SnappyFrameEncoder;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyZlibCodecFactoryTest {
    @Test
    void legacyImplementationInheritsStockWebSocketHandlers() {
        NettyZlibCodecFactory factory = DefaultNettyZlibCodecFactory.INSTANCE;
        var server = factory.newWebSocketServerCompressionHandler(12345);
        assertInstanceOf(WebSocketServerCompressionHandler.class, server);
        assertNotSame(server, factory.newWebSocketServerCompressionHandler(12345));
        assertSame(WebSocketClientCompressionHandler.INSTANCE, factory.newWebSocketClientCompressionHandler(23456));
    }

    @Test
    void legacyImplementationInheritsStockCodecs() {
        NettyZlibCodecFactory factory = DefaultNettyZlibCodecFactory.INSTANCE;
        assertEquals(Brotli.isAvailable(), factory.isBrotliAvailable());
        assertEquals(Zstd.isAvailable(), factory.isZstdAvailable());
        var encoder = factory.newSnappyFrameEncoder();
        var decoder = factory.newSnappyFrameDecoder();
        assertInstanceOf(SnappyFrameEncoder.class, encoder);
        assertInstanceOf(SnappyFrameDecoder.class, decoder);
        assertNotSame(encoder, factory.newSnappyFrameEncoder());
        assertNotSame(decoder, factory.newSnappyFrameDecoder());
        var outbound = new EmbeddedChannel(encoder);
        var inbound = new EmbeddedChannel(decoder);
        String body = "stock framed Snappy ".repeat(64);
        try {
            assertTrue(outbound.writeOutbound(Unpooled.copiedBuffer(body, StandardCharsets.UTF_8)));
            ByteBuf compressed;
            while ((compressed = outbound.readOutbound()) != null) {
                inbound.writeInbound(compressed);
            }
            ByteBuf decoded = inbound.readInbound();
            try {
                assertEquals(body, decoded.toString(StandardCharsets.UTF_8));
            } finally {
                decoded.release();
            }
        } finally {
            outbound.finishAndReleaseAll();
            inbound.finishAndReleaseAll();
        }
    }
}
