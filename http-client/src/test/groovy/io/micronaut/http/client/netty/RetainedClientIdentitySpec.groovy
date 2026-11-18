package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.ssl.ClientSslConfiguration
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioIoHandler
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

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The identity under which a client offers and takes back retained connections covers its TLS and proxy settings.
 */
class RetainedClientIdentitySpec extends Specification {

    EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    RecordingConnections retained = new RecordingConnections()
    List<DefaultHttpClient> clients = []

    def cleanup() {
        clients*.close()
        group.shutdownGracefully()
    }

    def 'clients share an identity only with equal TLS and proxy settings'() {
        given:
        def lax = new DefaultHttpClientConfiguration()
        lax.sslConfiguration = new ClientSslConfiguration(insecureTrustAllCertificates: true)
        def keyStore = new DefaultHttpClientConfiguration()
        keyStore.sslConfiguration.keyStore.password = 'secret-password'
        def noHostnameCheck = new DefaultHttpClientConfiguration()
        ((ClientSslConfiguration) noHostnameCheck.sslConfiguration).endpointIdentification = false
        def proxied = new DefaultHttpClientConfiguration()
        proxied.proxyType = Proxy.Type.HTTP
        proxied.proxyAddress = new InetSocketAddress('127.0.0.1', 1)

        when:
        String strict = identity(new DefaultHttpClientConfiguration())

        then:
        identity(new DefaultHttpClientConfiguration()) == strict
        [identity(lax), identity(keyStore), identity(noHostnameCheck), identity(proxied)].every { it != strict }
        identity(lax) != identity(keyStore)
        !identity(keyStore).contains('secret-password')
    }

    def 'a client with a #setting keeps no connections'() {
        given:
        def configuration = new DefaultHttpClientConfiguration()
        customize(configuration)

        when:
        client(configuration)

        then:
        retained.asked.isEmpty()

        where:
        setting             | customize
        'hostname verifier' | { DefaultHttpClientConfiguration c -> ((ClientSslConfiguration) c.sslConfiguration).hostnameVerifier = { h, s -> true } as HostnameVerifier }
        'ready SSL context' | { DefaultHttpClientConfiguration c -> c.sslConfiguration.sslContext = SSLContext.getDefault() }
        'proxy selector'    | { DefaultHttpClientConfiguration c -> c.proxySelector = ProxySelector.getDefault() }
    }

    def 'a refreshed client offers its connections under the identity of its new settings'() {
        given:
        Channel server = server()
        int port = ((InetSocketAddress) server.localAddress()).port
        def configuration = new DefaultHttpClientConfiguration()
        def client = client(configuration, URI.create("http://127.0.0.1:$port"))
        String before = retained.asked.last()
        def lax = new DefaultHttpClientConfiguration()
        lax.sslConfiguration = new ClientSslConfiguration(insecureTrustAllCertificates: true)
        String after = identity(lax)

        when:
        configuration.sslConfiguration = new ClientSslConfiguration(insecureTrustAllCertificates: true)
        client.connectionManager().refresh()
        String body = client.toBlocking().retrieve('/')
        new PollingConditions(timeout: 5).eventually {
            assert client.connectionManager().getChannels().size() == 1
        }
        client.close()

        then:
        body == 'foo'
        before != after
        new PollingConditions(timeout: 5).eventually {
            assert retained.offered == [after]
        }

        cleanup:
        server.close()
    }

    private String identity(DefaultHttpClientConfiguration configuration) {
        client(configuration)
        return retained.asked.last()
    }

    private DefaultHttpClient client(DefaultHttpClientConfiguration configuration, URI uri = URI.create('http://127.0.0.1:1')) {
        def client = DefaultHttpClient.builder()
            .loadBalancer(LoadBalancer.fixed(uri))
            .configuration(configuration)
            .eventLoopGroup(group)
            .retainedConnections(retained)
            .build()
        clients << client
        return client
    }

    private Channel server() {
        return new ServerBootstrap()
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
    }

    /**
     * Records the identities a client looks for and offers under, and keeps nothing.
     */
    static class RecordingConnections implements RetainedClientConnections {
        final List<String> asked = new CopyOnWriteArrayList<>()
        final List<String> offered = new CopyOnWriteArrayList<>()

        @Override
        boolean isRetainable(List<?> components) {
            return true
        }

        @Override
        boolean retain(String client, Object remote, Channel channel) {
            offered << client
            return false
        }

        @Override
        List<Map.Entry<Object, EventLoop>> retained(String client) {
            asked << client
            return []
        }

        @Override
        Channel adopt(String client, Object remote, EventLoop eventLoop) {
            return null
        }
    }
}
