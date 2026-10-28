package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2PingFrame
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2Settings
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

/**
 * An HTTP/2 peer with a zero flow-control window cannot keep stalled responses (and the
 * connection) alive past the idle timeout by sending PINGs.
 */
class H2ZeroWindowIdleTimeoutSpec extends Specification {

    void "a zero-window peer is closed after the idle timeout"(boolean ping) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'H2ZeroWindowIdleTimeoutSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.server.idle-timeout': '2s',
        ])
        def group = new NioEventLoopGroup(1)
        def discard = new Discard()
        Channel ch = new Bootstrap().group(group).channel(NioSocketChannel).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel c) throws Exception {
                c.pipeline().addLast(
                        Http2FrameCodecBuilder.forClient().initialSettings(Http2Settings.defaultSettings().initialWindowSize(0)).build(),
                        new Http2MultiplexHandler(discard))
            }
        }).connect(server.host, server.port).sync().channel()

        when:
        20.times {
            def stream = new Http2StreamChannelBootstrap(ch).handler(discard).open().sync().getNow()
            def headers = new DefaultHttp2Headers().method('GET').scheme('http').authority('localhost').path('/zero-window-big')
            stream.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true)).sync()
        }
        long deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline && ch.isActive()) {
            if (ping) {
                ch.writeAndFlush(new DefaultHttp2PingFrame(System.nanoTime()))
            }
            sleep(500)
        }

        then: "the server closed the stalled connection within 4x the idle timeout"
        !ch.isActive()

        cleanup:
        ch?.close()
        group?.shutdownGracefully()
        server?.close()

        where:
        ping << [false, true]
    }

    @ChannelHandler.Sharable
    static class Discard extends ChannelInboundHandlerAdapter {
        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ReferenceCountUtil.release(msg)
        }
    }

    @Requires(property = 'spec.name', value = 'H2ZeroWindowIdleTimeoutSpec')
    @Controller
    static class BigController {
        @Get('/zero-window-big')
        String big() {
            'x' * (32 * 1024)
        }
    }
}
