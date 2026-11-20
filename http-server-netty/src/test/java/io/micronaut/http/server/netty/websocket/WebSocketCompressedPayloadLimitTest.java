package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A permessage-deflate frame that inflates past maxPayloadLength (65536 by default) must not be delivered
 * inflated to a WebSocketFrame @OnMessage handler.
 */
class WebSocketCompressedPayloadLimitTest {
    private static final String SPEC_NAME = "WebSocketCompressedPayloadLimitTest";
    static final AtomicInteger MAX_DELIVERED = new AtomicInteger();

    @Test
    void inflatedFrameIsCappedByMaxPayloadLength() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())) {
                CompletableFuture<Void> handshake = new CompletableFuture<>();
                URI uri = URI.create("ws://127.0.0.1:" + server.getPort() + "/compressed-limit");
                Channel ch = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .remoteAddress("127.0.0.1", server.getPort())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel c) {
                            c.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(8192),
                                WebSocketClientCompressionHandler.INSTANCE,
                                new WebSocketClientProtocolHandler(WebSocketClientProtocolConfig.newBuilder()
                                    .webSocketUri(uri).allowExtensions(true).build()),
                                new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                        if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                            handshake.complete(null);
                                        }
                                    }

                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        ReferenceCountUtil.release(msg);
                                    }
                                });
                        }
                    }).connect().sync().channel();
                handshake.get(10, TimeUnit.SECONDS);
                ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[16 * 1024 * 1024]))).sync();
                Thread.sleep(2000);
                ch.close().sync();
                assertTrue(MAX_DELIVERED.get() <= 65536,
                    "server delivered an inflated frame of " + MAX_DELIVERED.get() + " bytes, maxPayloadLength is 65536");
            }
        }
    }

    @ServerWebSocket("/compressed-limit")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FrameSocket {
        @OnMessage
        void onMessage(WebSocketFrame frame) {
            MAX_DELIVERED.accumulateAndGet(frame.content().readableBytes(), Math::max);
            frame.release();
        }
    }
}
