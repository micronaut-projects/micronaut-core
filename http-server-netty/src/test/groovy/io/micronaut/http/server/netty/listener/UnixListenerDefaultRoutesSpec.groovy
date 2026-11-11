package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.unix.DomainSocketAddress
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpVersion
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * A UNIX listener keeps its own default route exposure when a TCP listener does not expose them.
 */
class UnixListenerDefaultRoutesSpec extends Specification {

    @IgnoreIf({ !Epoll.isAvailable() && !KQueue.isAvailable() })
    void "a UNIX listener serves the default routes it exposes, and not those it does not, next to a restricted TCP listener"() {
        given:
        def dir = Files.createTempDirectory('unix-listener')
        Files.write(dir.resolve('file.txt'), 'STATIC-CONTENT-OK'.bytes)
        Path open = dir.resolve('open.sock')
        Path closed = dir.resolve('closed.sock')
        int tcpPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'UnixListenerDefaultRoutesSpec',
                // UNIX domain sockets need a native transport
                'micronaut.netty.event-loops.default.prefer-native-transport': true,
                'micronaut.netty.event-loops.parent.prefer-native-transport': true,
                'micronaut.server.netty.listeners.tcp.port': tcpPort,
                'micronaut.server.netty.listeners.tcp.expose-default-routes': false,
                'micronaut.server.netty.listeners.open.family': 'UNIX',
                'micronaut.server.netty.listeners.open.path': open.toString(),
                'micronaut.server.netty.listeners.closed.family': 'UNIX',
                'micronaut.server.netty.listeners.closed.path': closed.toString(),
                'micronaut.server.netty.listeners.closed.expose-default-routes': false,
                'micronaut.router.static-resources.unix.paths': 'file:' + dir.toAbsolutePath(),
                'micronaut.router.static-resources.unix.mapping': '/assets/**',
        ])

        expect: 'the TCP listener does not expose the default routes'
        RestrictedListenerStaticResourceSpec.statusLine(tcpPort, '/unix-pub').contains(' 404 ')
        RestrictedListenerStaticResourceSpec.statusLine(tcpPort, '/assets/file.txt').contains(' 404 ')

        and: 'the unrestricted UNIX listener does'
        statusLine(open, '/unix-pub').contains(' 200 ')
        statusLine(open, '/assets/file.txt').contains(' 200 ')

        and: 'the restricted UNIX listener does not'
        statusLine(closed, '/unix-pub').contains(' 404 ')
        statusLine(closed, '/assets/file.txt').contains(' 404 ')

        cleanup:
        server?.close()
    }

    static String statusLine(Path socketPath, String path) {
        // the native transport of Netty, as java.net.UnixDomainSocketAddress requires Java 16
        boolean epoll = Epoll.isAvailable()
        EventLoopGroup group = epoll ? new EpollEventLoopGroup(1) : new KQueueEventLoopGroup(1)
        CompletableFuture<String> statusLine = new CompletableFuture<>()
        try {
            Channel channel = new Bootstrap()
                    .group(group)
                    .channel(epoll ? EpollDomainSocketChannel : KQueueDomainSocketChannel)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new HttpClientCodec())
                                    .addLast(new HttpObjectAggregator(1024 * 1024))
                                    .addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                                            statusLine.complete("${msg.protocolVersion()} ${msg.status()}".toString())
                                        }

                                        @Override
                                        void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                            statusLine.completeExceptionally(cause)
                                        }
                                    })
                        }
                    })
                    .connect(new DomainSocketAddress(socketPath.toString())).sync().channel()
            def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path)
            request.headers().set(HttpHeaderNames.HOST, 'localhost').set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
            channel.writeAndFlush(request).sync()
            return statusLine.get(10, TimeUnit.SECONDS)
        } finally {
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS)
        }
    }

    @Requires(property = 'spec.name', value = 'UnixListenerDefaultRoutesSpec')
    @Controller('/unix-pub')
    static class Pub {
        @Get
        String get() { 'pub' }
    }
}
