package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.ssl.util.SelfSignedCertificate
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The client follows the {@code Alt-Svc} header that the server sets by hand to its QUIC listener.
 */
class AltSvcHttp3Spec extends Specification {

    List<Path> tempFiles = []
    List<SelfSignedCertificate> certificates = []

    def cleanup() {
        tempFiles.each { Files.deleteIfExists(it) }
        certificates.each { it.delete() }
    }

    def 'requests move to HTTP/3 once the origin advertises it'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
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

    def 'the QUIC handshake authenticates the origin with a real trust store'() {
        given: 'a certificate for localhost only, trusted by the client'
        def certificate = certificate('localhost')
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", serverTls(certificate) + clientTrust([certificate]))
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'an alternative with a certificate for another host is not used'() {
        given: 'a QUIC server whose certificate is trusted, but issued for another name'
        def originCertificate = certificate('localhost')
        def otherCertificate = certificate('other.example')
        int roguePort = freeUdpPort()
        def rogue = ApplicationContext.run([
                'spec.name'                                  : 'AltSvcHttp3Spec',
                'alt-svc-spec.header'                        : 'clear',
                'micronaut.server.netty.listeners.quic.port' : roguePort,
                'micronaut.server.netty.listeners.quic.family': 'QUIC',
                'micronaut.server.netty.listeners.quic.ssl'  : true,
        ] + serverTls(otherCertificate))
        rogue.getBean(EmbeddedServer).start()

        and: 'an origin that advertises it'
        int tcpPort = SocketUtils.findAvailableTcpPort()
        def ctx = start(tcpPort, freeUdpPort(), "h3=\":$roguePort\"; ma=60",
                serverTls(originCertificate) + clientTrust([originCertificate, otherCertificate]))
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect: 'the handshake with the alternative fails, and the requests stay on TCP'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        cleanup:
        client?.close()
        ctx?.close()
        rogue?.close()
    }

    def 'a 421 from the alternative evicts it'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", [:])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        when: 'the alternative answers that it is not authoritative'
        client.toBlocking().retrieve('/alt-svc/misdirected')

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 421

        and: 'the next request goes to the origin again, which advertises the alternative anew'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'clear from the alternative removes it'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", [:])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/clear') == 'quic'
        client.toBlocking().retrieve('/alt-svc/clear') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/clear') == 'tcp'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'responses for another virtual host are not cached'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", [:])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/alt-svc/transport').header('Host', "other.example:$tcpPort")) == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'plain http origins are not moved'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", ['micronaut.server.netty.listeners.tcp.ssl': false])
        def client = ctx.createBean(HttpClient, new URL("http://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'

        cleanup:
        client?.close()
        ctx?.close()
    }

    def 'Alt-Svc is ignored unless enabled'() {
        given:
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
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
        int quicPort = freeUdpPort()
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

    def 'a proxied client ignores the header'() {
        given: 'a tunnelling proxy that counts its connections'
        def proxy = new TunnelProxy()
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", [
                'micronaut.http.client.proxy-type'   : 'http',
                'micronaut.http.client.proxy-address': "127.0.0.1:$proxy.port",
        ])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect: 'every request goes through the proxy over TCP'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        proxy.connects.get() >= 1

        cleanup:
        client?.close()
        ctx?.close()
        proxy?.close()
    }

    def 'a saturated QUIC connection is not marked as broken'() {
        given: 'one QUIC connection with room for two concurrent requests'
        int tcpPort = SocketUtils.findAvailableTcpPort()
        int quicPort = freeUdpPort()
        def ctx = start(tcpPort, quicPort, "h3=\":$quicPort\"; ma=60", [
                'micronaut.server.netty.http3.initial-max-streams-bidirectional': 2,
                'micronaut.http.client.pool.max-concurrent-http2-connections'  : 1,
        ])
        def client = ctx.createBean(HttpClient, new URL("https://localhost:$tcpPort"))

        expect:
        client.toBlocking().retrieve('/alt-svc/transport') == 'tcp'
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'

        when: 'more slow requests run at once than the connection has streams for'
        def results = Flux.range(0, 6)
                .flatMap({ client.retrieve(HttpRequest.GET('/alt-svc/slow'), String) }, 6)
                .collectList()
                .block(Duration.ofSeconds(30))

        then: 'the queued requests wait for the connection, and the alternative stays in use'
        results.size() == 6
        results.every { it == 'quic' }
        client.toBlocking().retrieve('/alt-svc/transport') == 'quic'

        cleanup:
        client?.close()
        ctx?.close()
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

    private static int freeUdpPort() {
        def socket = new DatagramSocket(0, InetAddress.getLoopbackAddress())
        try {
            return socket.localPort
        } finally {
            socket.close()
        }
    }

    private SelfSignedCertificate certificate(String fqdn) {
        def certificate = new SelfSignedCertificate(fqdn)
        certificates.add(certificate)
        return certificate
    }

    private Map<String, Object> serverTls(SelfSignedCertificate certificate) {
        def path = tempFile()
        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("key", certificate.key(), "".toCharArray(), new Certificate[]{certificate.cert()})
        try (OutputStream os = Files.newOutputStream(path)) {
            ks.store(os, "".toCharArray())
        }
        return [
                'micronaut.server.ssl.enabled'           : true,
                'micronaut.server.ssl.build-self-signed' : false,
                'micronaut.server.ssl.key-store.path'    : 'file://' + path,
                'micronaut.server.ssl.key-store.type'    : 'PKCS12',
                'micronaut.server.ssl.key-store.password': '',
        ]
    }

    private Map<String, Object> clientTrust(List<SelfSignedCertificate> trusted) {
        def path = tempFile()
        KeyStore ts = KeyStore.getInstance("JKS")
        ts.load(null, null)
        trusted.eachWithIndex { SelfSignedCertificate certificate, int i -> ts.setCertificateEntry("c$i", certificate.cert()) }
        try (OutputStream os = Files.newOutputStream(path)) {
            ts.store(os, "123456".toCharArray())
        }
        return [
                'micronaut.http.client.ssl.insecure-trust-all-certificates': false,
                'micronaut.http.client.ssl.trust-store.path'               : 'file://' + path,
                'micronaut.http.client.ssl.trust-store.type'               : 'JKS',
                'micronaut.http.client.ssl.trust-store.password'           : '123456',
        ]
    }

    private Path tempFile() {
        def path = Files.createTempFile("micronaut-alt-svc-test", ".store")
        tempFiles.add(path)
        return path
    }

    @Controller('/alt-svc')
    @Requires(property = 'spec.name', value = 'AltSvcHttp3Spec')
    static class AltSvcController {
        @Value('${alt-svc-spec.header}')
        String altSvc

        @Get(value = '/transport', produces = 'text/plain')
        HttpResponse<String> transport(HttpRequest<?> request) {
            return HttpResponse.ok(transportOf(request)).header('Alt-Svc', altSvc)
        }

        @Get(value = '/misdirected', produces = 'text/plain')
        HttpResponse<String> misdirected(HttpRequest<?> request) {
            if (transportOf(request) == 'quic') {
                return HttpResponse.status(HttpStatus.MISDIRECTED_REQUEST).header('Alt-Svc', altSvc)
            }
            return HttpResponse.ok('tcp').header('Alt-Svc', altSvc)
        }

        @Get(value = '/slow', produces = 'text/plain')
        CompletableFuture<HttpResponse<String>> slow(HttpRequest<?> request) {
            String transport = transportOf(request)
            // complete on the event loop of the request, which may allocate buffers in the test
            def response = new CompletableFuture<HttpResponse<String>>()
            ((NettyHttpRequest<?>) request).channelHandlerContext.executor().schedule({
                response.complete(HttpResponse.ok(transport).header('Alt-Svc', altSvc))
            } as Runnable, 500, TimeUnit.MILLISECONDS)
            return response
        }

        @Get(value = '/clear', produces = 'text/plain')
        HttpResponse<String> clear(HttpRequest<?> request) {
            String transport = transportOf(request)
            def response = HttpResponse.ok(transport)
            return transport == 'quic' ? response.header('Alt-Svc', 'clear') : response
        }

        private static String transportOf(HttpRequest<?> request) {
            def channel = ((NettyHttpRequest<?>) request).channelHandlerContext.channel()
            return channel.parent() instanceof QuicChannel ? 'quic' : 'tcp'
        }
    }

    /**
     * A proxy that tunnels CONNECT requests.
     */
    static class TunnelProxy implements Closeable {
        final ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        final AtomicInteger connects = new AtomicInteger()
        final List<Socket> sockets = Collections.synchronizedList([])

        TunnelProxy() {
            Thread.startDaemon {
                while (!server.closed) {
                    Socket socket
                    try {
                        socket = server.accept()
                    } catch (IOException ignored) {
                        return
                    }
                    sockets.add(socket)
                    Thread.startDaemon { tunnel(socket) }
                }
            }
        }

        int getPort() {
            return server.localPort
        }

        private void tunnel(Socket socket) {
            def input = socket.inputStream
            def head = new StringBuilder()
            while (!head.toString().endsWith('\r\n\r\n')) {
                int b = input.read()
                if (b < 0) {
                    socket.close()
                    return
                }
                head.append((char) b)
            }
            String[] authority = head.toString().split(' ')[1].split(':')
            def target = new Socket(authority[0], authority[1] as int)
            sockets.add(target)
            connects.incrementAndGet()
            socket.outputStream.write('HTTP/1.1 200 Connection established\r\n\r\n'.bytes)
            socket.outputStream.flush()
            Thread.startDaemon { pipe(target.inputStream, socket) }
            pipe(input, target)
        }

        private static void pipe(InputStream from, Socket to) {
            try {
                from.transferTo(to.outputStream)
            } catch (IOException ignored) {
            } finally {
                to.close()
            }
        }

        @Override
        void close() {
            server.close()
            sockets.each { it.close() }
        }
    }
}
