package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2SettingsAckFrame
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
     * Connect with h2c prior knowledge and return the connection window the server granted,
     * as seen by the client once the settings exchange is complete.
     */
    private static int connectionWindow(EmbeddedServer server) {
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
                            ch.pipeline().addLast(codec, new ChannelInboundHandlerAdapter() {
                                @Override
                                void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
                                    if (msg instanceof Http2SettingsAckFrame) {
                                        // the server acks our settings after it has sent its own preface and window update
                                        def connection = codec.connection()
                                        future.complete(connection.remote().flowController().windowSize(connection.connectionStream()))
                                    }
                                }

                                @Override
                                void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    future.completeExceptionally(cause)
                                }
                            })
                        }
                    })
            def channel = bootstrap.connect().sync().channel()
            try {
                return future.get(10, TimeUnit.SECONDS)
            } finally {
                channel.close().sync()
            }
        } finally {
            group.shutdownGracefully()
        }
    }

    def "server grants the configured connection window"(boolean legacy, Map<String, Object> properties, int expected) {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                    : 'Http2ConnectionWindowServerSpec',
                'micronaut.server.http-version'                : '2.0',
                'micronaut.server.ssl.enabled'                 : false,
                'micronaut.server.netty.legacy-multiplex-handlers': legacy,
        ] + properties)

        expect:
        connectionWindow(server) == expected

        cleanup:
        server.close()

        where:
        legacy | properties                                                                                                                        | expected
        false  | [:]                                                                                                                               | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        true   | [:]                                                                                                                               | Http2CodecUtil.DEFAULT_WINDOW_SIZE
        false  | ['micronaut.server.netty.http2.initial-window-size': MIB]                                                                         | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        true   | ['micronaut.server.netty.http2.initial-window-size': MIB]                                                                         | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        false  | ['micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB]                                                          | 4 * MIB
        true   | ['micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB]                                                          | 4 * MIB
        false  | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB] | 4 * MIB
        true   | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': 4 * MIB] | 4 * MIB
        false  | ['micronaut.server.netty.http2.initial-connection-window-size': 100000]                                                           | 100000
        true   | ['micronaut.server.netty.http2.initial-connection-window-size': 100000]                                                           | 100000
        false  | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': MIB]     | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
        true   | ['micronaut.server.netty.http2.initial-window-size': MIB, 'micronaut.server.netty.http2.initial-connection-window-size': MIB]     | Http2CodecUtil.DEFAULT_WINDOW_SIZE + 2 * (MIB - Http2CodecUtil.DEFAULT_WINDOW_SIZE)
    }
}
