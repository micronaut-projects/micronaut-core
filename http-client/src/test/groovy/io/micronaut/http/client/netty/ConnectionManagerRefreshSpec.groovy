package io.micronaut.http.client.netty

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

class ConnectionManagerRefreshSpec extends Specification {

    def 'refresh from a non event loop thread winds down pooled http1 connections'() {
        given:
        EventLoopGroup group = new NioEventLoopGroup(1)
        Channel server = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
                                        def response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                                Unpooled.copiedBuffer('foo', StandardCharsets.UTF_8))
                                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 3)
                                        ctx.writeAndFlush(response)
                                    }
                                })
                    }
                })
                .bind('127.0.0.1', 0).sync().channel()
        int port = ((InetSocketAddress) server.localAddress()).port
        DefaultHttpClient client = (DefaultHttpClient) HttpClient.create(new URL("http://127.0.0.1:$port"))

        when:
        def first = client.toBlocking().retrieve(HttpRequest.GET('/'))

        then:
        first == 'foo'
        new PollingConditions(timeout: 5).eventually {
            assert client.connectionManager().getChannels().size() == 1
        }

        when:
        def oldChannel = client.connectionManager().getChannels().first()
        // called from the test thread, which is not the connection's event loop
        client.connectionManager().refresh()

        then:
        noExceptionThrown()
        new PollingConditions(timeout: 5).eventually {
            assert !oldChannel.isActive()
        }

        when:
        def second = client.toBlocking().retrieve(HttpRequest.GET('/'))

        then:
        second == 'foo'
        client.connectionManager().getChannels().every { it != oldChannel }

        cleanup:
        client?.close()
        server?.close()
        group?.shutdownGracefully()
    }
}
