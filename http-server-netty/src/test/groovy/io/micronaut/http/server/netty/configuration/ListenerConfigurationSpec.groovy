package io.micronaut.http.server.netty.configuration

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDomainSocketChannel
import io.netty.channel.unix.DomainSocketAddress
import io.netty.channel.unix.Socket
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList

class ListenerConfigurationSpec extends Specification {
    def 'custom port'() {
        given:
        def customPort = SocketUtils.findAvailableTcpPort()
        NettyEmbeddedServer server = ApplicationContext.run(
                EmbeddedServer,
                [
                        'micronaut.server.netty.listeners.a.port': customPort
                ])

        expect:
        server.port == customPort
        server.boundPorts.asList() == [customPort]

        cleanup:
        server.close()
    }

    def 'random port'() {
        given:
        NettyEmbeddedServer server = ApplicationContext.run(
                EmbeddedServer,
                [
                        'micronaut.server.netty.listeners.a.port': -1,
                        'micronaut.server.netty.listeners.b.port': -1,
                ])

        expect:
        server.port != -1
        server.boundPorts.size() == 2
        !server.boundPorts.contains(-1)
        server.boundPorts.contains(server.port)

        cleanup:
        server.close()
    }

    def 'port before start'() {
        given:
        def ctx = ApplicationContext.run(
                [
                        'micronaut.server.netty.listeners.a.port': 1234,
                        'micronaut.server.netty.listeners.b.port': 5678,
                ])
        def server = ctx.getBean(NettyEmbeddedServer)

        expect:
        // which is picked is currently unspecified
        [1234, 5678].contains(server.port)
    }

    def 'port error messages'() {
        when:
        ApplicationContext.run([
                'micronaut.server.netty.listeners.a.port': -1,
                'micronaut.server.netty.listeners.b.port': -1,
        ]).getBean(NettyEmbeddedServer).port

        then:
        def e = thrown UnsupportedOperationException
        e.message.contains("random port")

        when:
        ApplicationContext.run([
                'micronaut.server.netty.listeners.a.family': 'unix',
                'micronaut.server.netty.listeners.a.path': '/foo',
                'micronaut.server.netty.listeners.b.port': -1,
        ]).getBean(NettyEmbeddedServer).port

        then:
        e = thrown UnsupportedOperationException
        e.message.contains("random port")

        when:
        ApplicationContext.run([
                'micronaut.server.netty.listeners.a.family': 'UNIX',
                'micronaut.server.netty.listeners.a.path': '/foo',
        ]).getBean(NettyEmbeddedServer).port

        then:
        e = thrown UnsupportedOperationException
        e.message.contains("unix domain socket")
    }

    def 'random port, ssl'() {
        given:
        def unsecuredPort = SocketUtils.findAvailableTcpPort()
        def server = (NettyEmbeddedServer) ApplicationContext.run(
                EmbeddedServer,
                [
                        'micronaut.server.ssl.enabled': true,
                        'micronaut.server.ssl.build-self-signed': true,
                        'micronaut.server.netty.listeners.a.port': -1,
                        'micronaut.server.netty.listeners.a.ssl': true,
                        'micronaut.server.netty.listeners.b.port': unsecuredPort,
                        'micronaut.server.netty.listeners.b.ssl': false,
                ])
        def securedPort = server.boundPorts.stream().filter(p -> p != unsecuredPort).findAny().get()

        when:
        def httpsConnection = (HttpsURLConnection) new URL('https://' + server.host + ':' + securedPort).openConnection()
        def sslCtx = SSLContext.getInstance("SSL")
        sslCtx.init(null, InsecureTrustManagerFactory.INSTANCE.trustManagers, new SecureRandom())
        httpsConnection.SSLSocketFactory = sslCtx.socketFactory
        httpsConnection.connect()
        httpsConnection.inputStream
        then:
        thrown FileNotFoundException

        when:
        def httpConnection = (HttpURLConnection) new URL('http://' + server.host + ':' + unsecuredPort).openConnection()
        httpConnection.connect()
        httpConnection.inputStream
        then:
        thrown FileNotFoundException

        cleanup:
        httpsConnection.disconnect()
        httpConnection.disconnect()
        server.close()
    }

    @IgnoreIf({ !Epoll.isAvailable() })
    def 'unix domain socket epoll'(boolean abstract_) {
        given:
        def tmpDir = Files.createTempDirectory(null)
        def path = tmpDir.resolve('test')
        def bindPath = (abstract_ ? '\0' : '') + path.toString()
        def server = (NettyEmbeddedServer) ApplicationContext.run(
                EmbeddedServer,
                [
                        'micronaut.netty.event-loops.default.transport': 'epoll',
                        'micronaut.netty.event-loops.parent.transport': 'epoll',
                        'micronaut.server.netty.listeners.a.family': 'UNIX',
                        'micronaut.server.netty.listeners.a.path': bindPath,
                ])
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()

        def group = new EpollEventLoopGroup()
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(EpollDomainSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(new DomainSocketAddress(bindPath))

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'))
        channel.read()
        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 1
        }
        responses[0].status() == HttpResponseStatus.NOT_FOUND

        cleanup:
        responses.forEach(r -> r.release())
        channel.close()
        group.shutdownGracefully()
        server.close()
        if (!abstract_) {
            Files.deleteIfExists(path)
        }
        Files.deleteIfExists(tmpDir)

        where:
        abstract_ << [true, false]
    }

    @IgnoreIf({ !os.linux })
    def 'unix domain socket nio'(boolean abstract_) {
        given:
        def tmpDir = Files.createTempDirectory(null)
        def path = tmpDir.resolve('test')
        def bindPath = (abstract_ ? '\0' : '') + path.toString()
        def server = (NettyEmbeddedServer) ApplicationContext.run(
                EmbeddedServer,
                [
                        'micronaut.netty.event-loops.default.transport': 'epoll',
                        'micronaut.netty.event-loops.parent.transport': 'epoll',
                        'micronaut.server.netty.listeners.a.family': 'UNIX',
                        'micronaut.server.netty.listeners.a.path': bindPath,
                ])
        def responses = new CopyOnWriteArrayList<FullHttpResponse>()

        def group = new NioEventLoopGroup()
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDomainSocketChannel)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(@NonNull Channel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
                                        responses.add(msg)
                                    }
                                })
                    }
                })
                .remoteAddress(UnixDomainSocketAddress.of(bindPath))

        when:
        def channel = bootstrap.connect().sync().channel()
        channel.writeAndFlush(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'))
        channel.read()
        then:
        new PollingConditions(timeout: 5).eventually {
            responses.size() == 1
        }
        responses[0].status() == HttpResponseStatus.NOT_FOUND

        cleanup:
        responses.forEach(r -> r.release())
        channel.close()
        group.shutdownGracefully()
        server.close()
        if (!abstract_) {
            Files.deleteIfExists(path)
        }
        Files.deleteIfExists(tmpDir)

        where:
        abstract_ << [
                // true, unsupported by nio
                false
        ]
    }

    @IgnoreIf({ !Epoll.isAvailable() })
    def 'attach to existing fd'() {
        given:
        // set up a server fd like systemd does
        def initCh = new EpollServerSocketChannel()
        def sock = (Socket) initCh.fd()
        sock.bind(new InetSocketAddress("127.0.0.1", 0))
        sock.listen(0)

        def server = (NettyEmbeddedServer) ApplicationContext.run(
                EmbeddedServer,
                [
                        'micronaut.netty.event-loops.default.transport': 'epoll',
                        'micronaut.netty.event-loops.parent.transport': 'epoll',
                        'micronaut.server.netty.listeners.a.bind': false,
                        'micronaut.server.netty.listeners.a.fd': sock.intValue(),
                ])
        def client = server.applicationContext.createBean(HttpClient, "http://localhost:" + sock.localAddress().port).toBlocking()

        when:
        client.retrieve("/")
        then:
        thrown HttpClientResponseException

        cleanup:
        client.close()
        server.close()
    }
}
