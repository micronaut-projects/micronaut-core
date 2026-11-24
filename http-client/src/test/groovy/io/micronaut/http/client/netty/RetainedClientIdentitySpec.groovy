package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.ssl.ClientSslConfiguration
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
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
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The identity under which a client offers and takes back retained connections covers its TLS settings.
 */
class RetainedClientIdentitySpec extends Specification {

    @Shared
    SelfSignedCertificate certificate = new SelfSignedCertificate()

    EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    RecordingConnections retained = new RecordingConnections()
    List<DefaultHttpClient> clients = []

    def cleanup() {
        clients*.close()
        group.shutdownGracefully()
    }

    def 'clients share an identity only with equal TLS settings, and only for the same retained connections'() {
        given:
        def lax = new DefaultHttpClientConfiguration()
        lax.sslConfiguration = new ClientSslConfiguration(insecureTrustAllCertificates: true)
        def noHostnameCheck = new DefaultHttpClientConfiguration()
        ((ClientSslConfiguration) noHostnameCheck.sslConfiguration).endpointIdentification = false
        def protocols = new DefaultHttpClientConfiguration()
        protocols.sslConfiguration.protocols = ['TLSv1.3'] as String[]

        when:
        String strict = identity(new DefaultHttpClientConfiguration())

        then:
        identity(new DefaultHttpClientConfiguration()) == strict
        [identity(lax), identity(noHostnameCheck), identity(protocols)].every { it != strict }

        when: 'another instance, with a key of its own'
        retained = new RecordingConnections()

        then:
        identity(new DefaultHttpClientConfiguration()) != strict
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
        'proxy address'     | { DefaultHttpClientConfiguration c -> c.proxyType = Proxy.Type.HTTP; c.proxyAddress = new InetSocketAddress('127.0.0.1', 1) }
        'trust store'       | { DefaultHttpClientConfiguration c -> trustStore(c) }
        'PEM key'           | { DefaultHttpClientConfiguration c -> c.sslConfiguration.keyStore.keyPath = 'file:' + certificate.privateKey().path; c.sslConfiguration.keyStore.certificatePath = 'file:' + certificate.certificate().path }
    }

    def 'a refreshed client offers its connections under the identity of its new settings'() {
        given:
        Channel server = server(null, true)
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

    def 'a connection opened before a refresh and established after it is not offered'() {
        given: 'a TLS server that reads nothing until it is let go'
        List<Channel> accepted = new CopyOnWriteArrayList<>()
        SslContext serverContext = SslContextBuilder.forServer(certificate.key(), certificate.cert()).build()
        Channel server = server(serverContext, false, accepted)
        int port = ((InetSocketAddress) server.localAddress()).port
        def configuration = new DefaultHttpClientConfiguration()
        configuration.sslConfiguration = new ClientSslConfiguration(insecureTrustAllCertificates: true)
        def client = client(configuration, URI.create("https://127.0.0.1:$port"))

        when: 'the connection is opened, and its handshake waits'
        def body = CompletableFuture.supplyAsync { client.toBlocking().retrieve('/') }
        new PollingConditions(timeout: 5).eventually {
            assert accepted.size() == 1
        }

        and: 'the client is refreshed to other settings before the handshake completes'
        configuration.sslConfiguration = new ClientSslConfiguration(insecureTrustAllCertificates: true, protocols: ['TLSv1.2', 'TLSv1.3'] as String[])
        client.connectionManager().refresh()
        accepted[0].config().setAutoRead(true)

        then:
        body.get(10, TimeUnit.SECONDS) == 'foo'

        when:
        new PollingConditions(timeout: 5).eventually {
            assert client.connectionManager().getChannels().size() == 1
        }
        client.close()

        then: 'it was made with the settings before the refresh, and is closed instead of offered'
        retained.offered.isEmpty()

        cleanup:
        server.close()
    }

    private void trustStore(DefaultHttpClientConfiguration configuration) {
        KeyStore store = KeyStore.getInstance('PKCS12')
        store.load(null, null)
        store.setCertificateEntry('server', certificate.cert())
        Path file = Files.createTempFile('trust', '.p12')
        file.toFile().deleteOnExit()
        Files.newOutputStream(file).withCloseable { store.store(it, 'secret'.toCharArray()) }
        configuration.sslConfiguration.trustStore.path = 'file:' + file
        configuration.sslConfiguration.trustStore.type = 'PKCS12'
        configuration.sslConfiguration.trustStore.password = 'secret'
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

    private Channel server(SslContext sslContext, boolean autoRead, List<Channel> accepted = []) {
        return new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel)
            .childOption(ChannelOption.AUTO_READ, autoRead)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    accepted << ch
                    if (sslContext != null) {
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc()))
                    }
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
        final SecretKeySpec key

        RecordingConnections() {
            byte[] bytes = new byte[32]
            new SecureRandom().nextBytes(bytes)
            key = new SecretKeySpec(bytes, 'HmacSHA256')
        }

        @Override
        boolean isRetainable(List<?> components) {
            return true
        }

        @Override
        Mac identityMac() {
            Mac mac = Mac.getInstance('HmacSHA256')
            mac.init(key)
            return mac
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
