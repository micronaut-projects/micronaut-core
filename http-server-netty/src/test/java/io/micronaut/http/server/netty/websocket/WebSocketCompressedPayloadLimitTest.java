package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A permessage-deflate frame must not inflate past the largest maxPayloadLength of the server
 * endpoints: the inflater fails (1011) before the whole payload is inflated and checked against
 * the limit (1009).
 */
class WebSocketCompressedPayloadLimitTest {
    private static final String SPEC_NAME = "WebSocketCompressedPayloadLimitTest";

    @Test
    void inflationIsCappedByMaxPayloadLength() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("spec.name", SPEC_NAME);
        config.put("micronaut.server.port", -1);
        try (ApplicationContext ctx = ApplicationContext.run(config)) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            EventLoopGroup group = new NioEventLoopGroup(1);
            try {
                CompletableFuture<Void> handshake = new CompletableFuture<>();
                CompletableFuture<Integer> closeStatus = new CompletableFuture<>();
                URI uri = URI.create("ws://127.0.0.1:" + server.getPort() + "/compressed-limit");
                Channel ch = new Bootstrap().group(group).channel(NioSocketChannel.class)
                    .remoteAddress("127.0.0.1", server.getPort())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel c) {
                            c.pipeline().addLast(new HttpClientCodec(), new HttpObjectAggregator(8192),
                                WebSocketClientCompressionHandler.INSTANCE,
                                new WebSocketClientProtocolHandler(WebSocketClientProtocolConfig.newBuilder()
                                    .webSocketUri(uri).allowExtensions(true).handleCloseFrames(false).build()),
                                new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                        if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                            handshake.complete(null);
                                        }
                                    }

                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        if (msg instanceof CloseWebSocketFrame) {
                                            closeStatus.complete(((CloseWebSocketFrame) msg).statusCode());
                                        }
                                        ReferenceCountUtil.release(msg);
                                    }
                                });
                        }
                    }).connect().sync().channel();
                handshake.get(10, TimeUnit.SECONDS);
                // 16 MiB of zeros, a few KiB on the wire
                ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[16 * 1024 * 1024]))).sync();
                assertEquals(CloseReason.INTERNAL_ERROR.getCode(), closeStatus.get(10, TimeUnit.SECONDS));
                ch.close().sync();
            } finally {
                group.shutdownGracefully();
            }
        }
    }

    @ServerWebSocket("/compressed-limit")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BytesSocket {
        @OnMessage
        void onMessage(byte[] message) {
        }
    }
}
