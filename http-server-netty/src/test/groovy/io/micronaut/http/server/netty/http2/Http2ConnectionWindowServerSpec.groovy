package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpClientUpgradeHandler
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.util.ReferenceCountUtil
import org.jspecify.annotations.NonNull
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Checks that the configured connection window reaches a real client through the server
 * pipeline, for both the default and the legacy HTTP/2 handlers.
 */
class Http2ConnectionWindowServerSpec extends Specification {
    private static final int MIB = 1024 * 1024

    /**
     * Connect over h2c, with prior knowledge or through an HTTP/1.1 upgrade, and return the
     * connection window the server granted, as seen by the client once the settings exchange is
     * complete.
     */
    private static int connectionWindow(EmbeddedServer server, boolean upgrade) {
        def group = new NioEventLoopGroup(1)
        try {
            def future = new CompletableFuture<Integer>()
            def bootstrap = new Bootstrap()
                    .remoteAddress(server.host, server.port)
                    .group(group)
                    .channel(NioSocketChannel)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(@NonNull SocketChannel ch) {
                            Http2FrameCodec codec = Http2FrameCodecBuilder.forClient().build()
                            def listener = new ChannelInboundHandlerAdapter() {
                                @Override
                                void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
                                    try {
                                        if (msg instanceof Http2SettingsAckFrame) {
                                            // the server acks our settings after it has sent its own preface and window update
                                            def connection = codec.connection()
                                            future.complete(connection.remote().flowController().windowSize(connection.connectionStream()))
                                        }
                                    } finally {
                                        // after an upgrade the response to the upgrade request arrives here too
                                        ReferenceCountUtil.release(msg)
                                    }
                                }

                                @Override
                                void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    future.completeExceptionally(cause)
                                }
                            }
                            if (upgrade) {
                                // the upgrade handler adds the Upgrade and HTTP2-Settings headers to the first
                                // request; once the server accepts, the upgrade codec installs the handler it
                                // is given, which here installs the frame codec (sending the preface) and the listener
                                def sourceCodec = new HttpClientCodec()
                                def upgradeCodec = new Http2ClientUpgradeCodec(codec, new ChannelInitializer<Channel>() {
                                    @Override
                                    protected void initChannel(@NonNull Channel c) {
                                        c.pipeline().addLast(codec, listener)
                                    }
                                })
                                ch.pipeline().addLast(sourceCodec, new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536), new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                        if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
                                            future.completeExceptionally(new IllegalStateException("h2c upgrade rejected"))
                                        }
                                        ctx.fireUserEventTriggered(evt)
                                    }
                                })
                            } else {
                                ch.pipeline().addLast(codec, listener)
                            }
                        }
                    })
            def channel = bootstrap.connect().sync().channel()
            try {
                if (upgrade) {
                    channel.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')).sync()
                }
                return future.get(10, TimeUnit.SECONDS)
            } finally {
                channel.close().sync()
            }
        } finally {
            group.shutdownGracefully()
        }
    }

    def "server grants the configured connection window (legacy=#legacy, upgrade=#upgrade, #properties)"(boolean legacy, boolean upgrade, Map<String, Object> properties, int expected) {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'Http2ConnectionWindowServerSpec',
                'micronaut.server.http-version'                : '2.0',
                'micronaut.server.ssl.enabled'                 : false,
                'micronaut.server.netty.legacy-multiplex-handlers': legacy,
        ] + properties)

        expect:
        connectionWindow(server, upgrade) == expected

        cleanup:
        server.close()

        where:
        legacy | upgrade | properties                                                                                                                        | expected
        false  | false | [:]                                                                                                                               | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        false  | true  | [:]                                                                                                                               | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        true   | false | [:]                                                                                                                               | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        true   | true  | [:]                                                                                                                               | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        false  | false | ['micronaut.server.netty.http2.initial-window-size': MIB]                                                                         | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        false  | true  | ['micronaut.server.netty.http2.initial-window-size': MIB]                                                                         | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        true   | false | ['micronaut.server.netty.http2.initial-window-size': MIB]                                                                         | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        true   | true  | ['micronaut.server.netty.http2.initial-window-size': MIB]                                                                         | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        false  | false | ['micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB]                                                          | 4 * MIB
        false  | true  | ['micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB]                                                          | 4 * MIB
        true   | false | ['micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB]                                                          | 4 * MIB
        true   | true  | ['micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB]                                                          | 4 * MIB
        false  | false | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB] | 4 * MIB
        false  | true  | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB] | 4 * MIB
        true   | false | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB] | 4 * MIB
        true   | true  | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB] | 4 * MIB
        false  | false | ['micronaut.server.netty.http2.initial-connection-window-size': 100000]                                                           | 100000
        false  | true  | ['micronaut.server.netty.http2.initial-connection-window-size': 100000]                                                           | 100000
        true   | false | ['micronaut.server.netty.http2.initial-connection-window-size': 100000]                                                           | 100000
        true   | true  | ['micronaut.server.netty.http2.initial-connection-window-size': 100000]                                                           | 100000
        false  | false | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': MIB]     | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        false  | true  | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': MIB]     | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        true   | false | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': MIB]     | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        true   | true  | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': MIB]     | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
    }
}
