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
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

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

        when:
        server.stop()
        server.start()
        def h1 = client(server, 'http/1.1')
        def h3 = client(server, 'h3')

        then:
        h1.client.toBlocking().exchange(HttpRequest.GET('/alt-svc'), String).header('Alt-Svc') == "h3=\":${server.port}\""
        h3.client.toBlocking().retrieve('/alt-svc') == 'ok'

        cleanup:
        h1?.close()
        h3?.close()
        server?.close()
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
