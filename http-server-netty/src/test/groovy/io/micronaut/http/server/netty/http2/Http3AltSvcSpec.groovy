package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.context.watch.ReloadingConfigurationWatcher
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.http.server.netty.ssl.SelfSignedSslBuilder
import io.micronaut.http.ssl.CertificateProvider
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.ssl.util.SelfSignedCertificate
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Sinks
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.security.KeyStore
import java.time.Duration
import java.security.cert.Certificate

/**
 * HTTP/3 on the HTTPS port ({@code micronaut.server.netty.http3.enabled}) and its
 * advertisement with an {@code Alt-Svc} header.
 */
class Http3AltSvcSpec extends Specification {

    private static final Map<String, Object> SERVER = [
            'spec.name'                       : 'Http3AltSvcSpec',
            'micronaut.server.ssl.enabled'    : true,
            'micronaut.server.ssl.buildSelfSigned': true,
            'micronaut.server.ssl.port'       : -1,
    ]

    def 'HTTP/3 is served on the HTTPS port and advertised over HTTP/1.1'() {
        given:
        def ctx = ApplicationContext.run(SERVER + ['micronaut.server.netty.http3.enabled': true])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')
        def h3 = client(server, 'h3')

        when:
        HttpResponse<String> response = h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String)

        then:
        response.body() == 'ok'
        response.header('Alt-Svc') == "h3=\":${server.port}\""

        when:
        // the client only speaks HTTP/3, so it reaches the server over QUIC
        HttpResponse<String> h3Response = h3.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String)

        then: 'the HTTP/3 listener shares the port of the HTTPS listener and does not advertise itself'
        h3Response.body() == 'ok'
        h3Response.header('Alt-Svc') == null

        cleanup:
        h1?.close()
        h3?.close()
        server?.close()
        ctx?.close()
    }

    def 'HTTP/3 is advertised over HTTP/2'() {
        given:
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.http3.enabled': true,
                'micronaut.server.http-version'       : '2.0',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h2 = client(server, 'h2')

        when:
        HttpResponse<String> response = h2.client.toBlocking().exchange(HttpRequest.GET('/alt-svc/version'), String)

        then:
        response.body() == HttpVersion.HTTP_2_0.name()
        response.header('Alt-Svc') == "h3=\":${server.port}\""

        cleanup:
        h2?.close()
        server?.close()
        ctx?.close()
    }

    def 'HTTP/3 on a fixed HTTPS port, with a max age'() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.ssl.port'                   : port,
                'micronaut.server.netty.http3.enabled'        : true,
                'micronaut.server.netty.http3.alt-svc-max-age': '1h',
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')
        def h3 = client(server, 'h3')

        expect:
        server.port == port
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":$port\"; ma=3600"
        h3.client.toBlocking().retrieve('/alt-svc') == 'ok'

        cleanup:
        h1?.close()
        h3?.close()
        server?.close()
        ctx?.close()
    }

    def 'the advertisement can be disabled'() {
        given:
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.http3.enabled': true,
                'micronaut.server.netty.http3.alt-svc': false,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')
        def h3 = client(server, 'h3')

        expect:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null
        h3.client.toBlocking().retrieve('/alt-svc') == 'ok'

        cleanup:
        h1?.close()
        h3?.close()
        server?.close()
        ctx?.close()
    }

    def 'a response keeps an Alt-Svc header it sets itself'() {
        given:
        def ctx = ApplicationContext.run(SERVER + ['micronaut.server.netty.http3.enabled': true])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc/custom'), String).header('Alt-Svc') == 'clear'

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'HTTP/3 is not served nor advertised without TLS'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                           : 'Http3AltSvcSpec',
                'micronaut.server.netty.http3.enabled': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = ctx.createBean(HttpClient, server.URI)

        expect:
        server.boundPorts.size() == 1
        h1.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'nothing is advertised by default'() {
        given:
        def ctx = ApplicationContext.run(SERVER)
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'an explicit QUIC listener can be advertised'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.listeners.tcp.port'   : tcpPort,
                'micronaut.server.netty.listeners.tcp.ssl'    : true,
                'micronaut.server.netty.listeners.quic.family': 'QUIC',
                'micronaut.server.netty.listeners.quic.port'  : quicPort,
                'micronaut.server.netty.http3.alt-svc'        : true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":$quicPort\""

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'the HTTP/3 listener follows the HTTPS listener to a new random port on restart'() {
        given:
        def ctx = ApplicationContext.run(SERVER + ['micronaut.server.netty.http3.enabled': true])
        def server = ctx.getBean(EmbeddedServer)
        server.start()

        int firstPort = server.port

        when: 'the old port is taken while the server restarts, so that it gets another one'
        server.stop()
        def blocker = new ServerSocket(firstPort)
        server.start()
        def h1 = client(server, 'http/1.1')
        def h3 = client(server, 'h3')

        then:
        server.port != firstPort
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":${server.port}\""
        h3.client.toBlocking().retrieve('/alt-svc') == 'ok'

        cleanup:
        blocker?.close()
        h1?.close()
        h3?.close()
        server?.close()
        ctx?.close()
    }

    def 'the plain HTTP listener of a dual protocol server does not advertise HTTP/3'() {
        given:
        int httpPort = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.dual-protocol'      : true,
                'micronaut.server.port'               : httpPort,
                'micronaut.server.netty.http3.enabled': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def plain = ctx.createBean(HttpClient, new URI("http://localhost:$httpPort"))
        def h1 = client(server, 'http/1.1')

        expect:
        plain.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":${server.port}\""

        cleanup:
        plain?.close()
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'the server does not start when the UDP port of the HTTPS port is taken'() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        def udp = new DatagramSocket(port)

        when:
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.ssl.port'           : port,
                'micronaut.server.netty.http3.enabled': true,
        ])
        ctx.getBean(EmbeddedServer).start()

        then:
        def e = thrown(ServerStartupException)
        e.message.contains('quic:')

        cleanup:
        udp.close()
        ctx?.close()
    }

    def 'a mapped port is advertised'() {
        given:
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.http3.enabled'     : true,
                'micronaut.server.netty.http3.alt-svc-port': 443,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == 'h3=":443"'

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'an HTTPS listener prefers the QUIC listener on its own port'() {
        given:
        int https1 = SocketUtils.findAvailableTcpPort()
        int https2 = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.listeners.a.port'   : https1,
                'micronaut.server.netty.listeners.a.ssl'    : true,
                'micronaut.server.netty.listeners.b.family': 'QUIC',
                'micronaut.server.netty.listeners.b.port'  : https1,
                'micronaut.server.netty.listeners.c.port'   : https2,
                'micronaut.server.netty.listeners.c.ssl'    : true,
                'micronaut.server.netty.listeners.d.family': 'QUIC',
                'micronaut.server.netty.listeners.d.port'  : https2,
                'micronaut.server.netty.http3.alt-svc'      : true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def clientCtx = ApplicationContext.run(['micronaut.http.client.ssl.insecure-trust-all-certificates': true])
        def first = clientCtx.createBean(HttpClient, new URI("https://localhost:$https1"))
        def second = clientCtx.createBean(HttpClient, new URI("https://localhost:$https2"))

        expect:
        first.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":$https1\""
        second.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":$https2\""

        cleanup:
        first?.close()
        second?.close()
        clientCtx?.close()
        server?.close()
        ctx?.close()
    }

    def 'a QUIC listener on another address than the HTTPS listener is not advertised'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.listeners.tcp.port'   : tcpPort,
                'micronaut.server.netty.listeners.tcp.ssl'    : true,
                'micronaut.server.netty.listeners.quic.family': 'QUIC',
                'micronaut.server.netty.listeners.quic.host'  : '127.0.0.1',
                'micronaut.server.netty.listeners.quic.port'  : quicPort,
                'micronaut.server.netty.http3.alt-svc'        : true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect: 'the HTTPS listener is on the wildcard address, and its remote clients could not reach the QUIC listener'
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'a changed advertisement applies without a restart'() {
        given:
        def ctx = ApplicationContext.run(SERVER + ['micronaut.server.netty.http3.enabled': true])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')
        def context = (DefaultBeanContext) ctx

        expect: 'a connection that is open before the change'
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":${server.port}\""

        when:
        ctx.getBean(NettyHttpServerConfiguration).http3.altSvcMaxAge = Duration.ofMinutes(5)
        def outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(['micronaut.server.netty.http3.alt-svc-max-age'] as Set))

        then:
        outcomes == [ReloadingConfigurationWatcher.Outcome.APPLIED]
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":${server.port}\"; ma=300"

        when: 'a setting of the QUIC codec changes'
        outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(['micronaut.server.netty.http3.initial-max-data'] as Set))

        then:
        outcomes == [ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART]

        when: 'the TLS protocols change, which decide whether HTTP/3 can be served'
        outcomes = context.notifyConfigurationChange(ConfigurationChange.ofKeys(['micronaut.server.ssl.protocols'] as Set))

        then:
        outcomes == [ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART]

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'HTTP/3 starts while the certificate is pending, and is advertised once it is loaded'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                           : 'Http3AltSvcSpec',
                'micronaut.server.ssl.enabled'        : true,
                'micronaut.server.ssl.port'           : -1,
                'micronaut.server.ssl.key-name'       : 'h3-deferred',
                'micronaut.server.netty.http3.enabled': true,
        ])
        def server = ctx.getBean(EmbeddedServer)

        when: 'the server starts before the certificate provider has emitted'
        server.start()

        then:
        server.running

        when:
        DeferredCertificateProvider provider = ctx.getBean(DeferredCertificateProvider)
        provider.emit()
        def h1 = client(server, 'http/1.1')
        def h3 = client(server, 'h3')

        then:
        new PollingConditions(timeout: 10).eventually {
            assert h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":${server.port}\""
        }
        h3.client.toBlocking().retrieve('/alt-svc') == 'ok'

        cleanup:
        h1?.close()
        h3?.close()
        server?.close()
        ctx?.close()
    }

    def 'HTTP/3 is not served when the TLS protocols exclude TLS 1.3'() {
        given:
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.ssl.protocols'      : ['TLSv1.2'],
                'micronaut.server.netty.http3.enabled': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect: 'no HTTP/3 listener, so nothing to advertise'
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'an explicit QUIC listener that does not expose the default routes is not advertised by one that does'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run(SERVER + [
                'micronaut.server.netty.listeners.tcp.port'                   : tcpPort,
                'micronaut.server.netty.listeners.tcp.ssl'                    : true,
                'micronaut.server.netty.listeners.quic.family'                : 'QUIC',
                'micronaut.server.netty.listeners.quic.port'                  : quicPort,
                'micronaut.server.netty.listeners.quic.expose-default-routes': false,
                'micronaut.server.netty.http3.alt-svc'                        : true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def h1 = client(server, 'http/1.1')

        expect:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == null

        cleanup:
        h1?.close()
        server?.close()
        ctx?.close()
    }

    def 'the HTTPS and HTTP/3 listeners present the same self-signed certificate'() {
        given:
        def ctx = ApplicationContext.run(SERVER + ['micronaut.server.netty.http3.enabled': true])
        SelfSignedSslBuilder builder = ctx.getBean(SelfSignedSslBuilder)
        def first = builder.getKeyStore(builder.sslConfiguration).get()
        def second = builder.getKeyStore(builder.sslConfiguration).get()

        expect:
        first.getCertificate('key') == second.getCertificate('key')

        cleanup:
        ctx?.close()
    }

    /**
     * A client that only speaks the given protocol, e.g. {@code h3} for HTTP/3 over QUIC.
     */
    private static ClientHolder client(EmbeddedServer server, String alpn) {
        def clientCtx = ApplicationContext.run([
                'micronaut.http.client.alpn-modes'                         : [alpn],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        return new ClientHolder(clientCtx, clientCtx.createBean(HttpClient, new URI("https://localhost:${server.port}")))
    }

    private static final class ClientHolder implements Closeable {
        final ApplicationContext ctx
        final HttpClient client

        ClientHolder(ApplicationContext ctx, HttpClient client) {
            this.ctx = ctx
            this.client = client
        }

        @Override
        void close() {
            client.close()
            ctx.close()
        }
    }

    @Singleton
    @Named('h3-deferred')
    @Requires(property = 'spec.name', value = 'Http3AltSvcSpec')
    static class DeferredCertificateProvider implements CertificateProvider {
        private final Sinks.Many<KeyStore> keyStores = Sinks.many().replay().latest()

        @Override
        String getName() {
            'h3-deferred'
        }

        @Override
        Publisher<KeyStore> getKeyStore() {
            keyStores.asFlux()
        }

        void emit() {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType())
            store.load(null, null)
            def ssc = new SelfSignedCertificate()
            store.setKeyEntry('key', ssc.key(), null, [ssc.cert()] as Certificate[])
            keyStores.tryEmitNext(store).orThrow()
        }
    }

    @Controller('/alt-svc')
    @Requires(property = 'spec.name', value = 'Http3AltSvcSpec')
    static class Ctrl {
        @Get
        String get() {
            'ok'
        }

        @Get('/version')
        String version(HttpRequest<?> request) {
            request.httpVersion.name()
        }

        @Get('/custom')
        HttpResponse<String> custom() {
            HttpResponse.ok('custom').header('Alt-Svc', 'clear')
        }
    }
}
