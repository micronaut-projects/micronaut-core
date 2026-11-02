package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * An HTTP/3 listener exposes the default routes while another listener does not.
 */
class Http3ListenerExposureSpec extends Specification {
    def 'default route on a QUIC listener next to a restricted listener'() {
        given:
        int quicPort = SocketUtils.findAvailableTcpPort()
        int restricted = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run([
                'spec.name': 'Http3ListenerExposureSpec',
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.netty.listeners.a.family': 'QUIC',
                'micronaut.server.netty.listeners.a.port': quicPort,
                'micronaut.server.netty.listeners.b.port': restricted,
                'micronaut.server.netty.listeners.b.ssl': false,
                'micronaut.server.netty.listeners.b.expose-default-routes': false,

                "micronaut.http.client.alpn-modes" : ["h3"],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, new URI("https://localhost:$quicPort"))

        expect:
        client.toBlocking().retrieve("/h3-exposure") == 'pub'

        cleanup:
        client?.close()
        server?.close()
        ctx?.close()
    }

    @Controller
    @Requires(property = "spec.name", value = "Http3ListenerExposureSpec")
    static class Ctrl {
        @Get('/h3-exposure')
        String get() {
            'pub'
        }
    }
}
