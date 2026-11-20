package io.micronaut.http.server.netty.handler;

import io.micronaut.http.netty.compression.DefaultNettyZlibCodecFactory;
import io.micronaut.http.netty.compression.NettyZlibCodecFactory;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.compression.CompressionException;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressorDiscardTest {
    private static final HttpCompressionStrategy STRATEGY = new HttpCompressionStrategy() {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public int getMaxZstdEncodeSize() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean shouldCompress(HttpResponse ignored) {
            return true;
        }
    };

    @Test
    void discardAfterFinishReleasesQueuedOutputAndIsIdempotent() {
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            var session = new Compressor(STRATEGY).prepare(channel.pipeline().firstContext(), request, response, 3);
            assertNotNull(session);
            var input = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
            session.push(input);
            session.finish();
            session.discard();
            session.discard();
            assertEquals(0, input.refCnt());
            assertNull(session.poll());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void discardAfterStreamedFinishReleasesQueuedOutputAndIsIdempotent() {
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            var session = new Compressor(STRATEGY).prepare(channel.pipeline().firstContext(), request, response, 3);
            assertNotNull(session);
            var input = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
            var more = Unpooled.wrappedBuffer(new byte[]{4, 5, 6});
            session.push(input);
            session.push(more);
            session.finish();
            session.discard();
            session.discard();
            assertEquals(0, input.refCnt());
            assertEquals(0, more.refCnt());
            assertNull(session.poll());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void discardSwallowsTheFailureOfAnEncoderThatFailsToClose() {
        var failing = new NettyZlibCodecFactory() {
            @Override
            public ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel) {
                return DefaultNettyZlibCodecFactory.INSTANCE.newZlibEncoder(wrapper, compressionLevel, windowBits, memLevel);
            }

            @Override
            public ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
                return DefaultNettyZlibCodecFactory.INSTANCE.newZlibDecoder(wrapper, maxAllocation);
            }

            @Override
            public ChannelHandler newSnappyFrameEncoder() {
                return new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                        ReferenceCountUtil.release(msg);
                        throw new CompressionException("encode failed");
                    }

                    @Override
                    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                        ctx.fireExceptionCaught(new CompressionException("close failed"));
                        ctx.close(promise);
                    }
                };
            }
        };
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "snappy");
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            var session = new Compressor(STRATEGY, failing).prepare(channel.pipeline().firstContext(), request, response, 3);
            assertNotNull(session);
            var input = Unpooled.wrappedBuffer(new byte[]{1, 2, 3});
            assertThrows(EncoderException.class, () -> session.push(input));
            assertDoesNotThrow(session::discard);
            assertEquals(0, input.refCnt());
            assertNull(session.poll());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void pushAfterFinishReleasesThePiece() {
        var channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        try {
            var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
            var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            var session = new Compressor(STRATEGY).prepare(channel.pipeline().firstContext(), request, response, 3);
            assertNotNull(session);
            session.push(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}));
            session.finish();
            var late = Unpooled.wrappedBuffer(new byte[]{4});
            assertThrows(IllegalStateException.class, () -> session.push(late));
            assertEquals(0, late.refCnt());
            session.discard();
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
