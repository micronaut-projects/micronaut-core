package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.quic.QuicChannel
import spock.lang.Specification

/**
 * The client follows the {@code Alt-Svc} header that the server sets by hand to its QUIC listener.
 */
class AltSvcHttp3Spec extends Specification {

    def 'requests move to HTTP/3 once the origin advertises it'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = SocketUtils.findAvailableTcpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", [:])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect: 'the first request goes over TCP and learns the alternative'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        and: 'the following requests go over QUIC'
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'Alt-Svc is ignored unless enabled'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = SocketUtils.findAvailableTcpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", ['micronaut.http.client.follow-alt-svc': false])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'requests fall back to TCP when the alternative does not answer'() {
        given: 'an alternative on a UDP port that swallows every datagram'
        def silent = new DatagramSocket(0, InetAddress.getLoopbackAddress())
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = SocketUtils.findAvailableTcpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$silent.localPort\"; ma=60", ['micronaut.http.client.connect-timeout': '500ms'])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect: 'the first request learns the alternative'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        and: 'the request that tries the alternative still succeeds, over TCP'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        when: 'the alternative is marked as broken'
        long start = System.nanoTime()
        def transport = client.toBlocking().retrieve('/alt-svc/transport')
        long millis = (System.nanoTime() - start) / 1_000_000

        then: 'later requests go straight to TCP'
        transport == 'tcp'
        millis < 500

        cleanup:
        client?.close()
        ctx?.close()
        silent?.close()
    }

    private static ApplicationContext start(int tcpPort, int quicPort, String altSvc, Map<String, Object> extra) {
        def ctx = ApplicationContext.run([
                'spec.name'                                               : 'AltSvcHttp3Spec',
                'alt-svc-spec.header'                                     : altSvc,
                'micronaut.server.ssl.enabled'                            : true,
                'micronaut.server.ssl.build-self-signed'                  : true,
                'micronaut.server.netty.listeners.tcp.port'               : tcpPort,
                'micronaut.server.netty.listeners.tcp.ssl'                : true,
                'micronaut.server.netty.listeners.quic.port'              : quicPort,
                'micronaut.server.netty.listeners.quic.family'            : 'QUIC',
                'micronaut.server.netty.listeners.quic.ssl'               : true,
                'micronaut.http.client.follow-alt-svc'                    : true,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ] + extra)
        ctx.getBean(EmbeddedServer).start()
        return ctx
    }

    @Controller('/alt-svc')
    @Requires(property = 'spec.name', value = 'AltSvcHttp3Spec')
    static class AltSvcController {
        @Value('${alt-svc-spec.header}')
        String altSvc

        @Get(value = '/transport', produces = 'text/plain')
        HttpResponse<String> transport(HttpRequest<?> request) {
            def channel = ((NettyHttpRequest<?>) request).channelHandlerContext.channel()
            String transport = channel.parent() instanceof QuicChannel ? 'quic' : 'tcp'
            return HttpResponse.ok(transport).header('Alt-Svc', altSvc)
        }
    }
}
