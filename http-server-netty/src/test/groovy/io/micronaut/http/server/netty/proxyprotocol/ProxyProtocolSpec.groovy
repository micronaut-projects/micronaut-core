package io.micronaut.http.server.netty.proxyprotocol

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * PROXY protocol (v1 and v2) support of the Netty HTTP server.
 */
class ProxyProtocolSpec extends Specification {
    static final String LOGGER = 'proxy-protocol-access-log'
    static final byte[] V2_SIGNATURE = [0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A] as byte[]

    ListAppender<ILoggingEvent> appender

    def setup() {
        appender = new ListAppender<>()
        appender.start()
        ((Logger) LoggerFactory.getLogger(LOGGER)).addAppender(appender)
    }

    def cleanup() {
        ((Logger) LoggerFactory.getLogger(LOGGER)).detachAppender(appender)
    }

    private static Map<String, Object> accessLog() {
        [
                'micronaut.server.netty.access-logger.enabled'    : true,
                'micronaut.server.netty.access-logger.logger-name': LOGGER,
                'micronaut.server.netty.access-logger.log-format' : '%a %r',
        ]
    }

    private EmbeddedServer start(Map<String, Object> props) {
        ApplicationContext.run(EmbeddedServer, ['spec.name': 'ProxyProtocolSpec'] + accessLog() + props)
    }

    private List<String> accessLines() {
        new ArrayList<>(appender.list).collect { it.formattedMessage }
    }

    /**
     * Whether the access log gets a line starting with the given prefix. The access log is
     * written after the response, on the event loop, and other requests (e.g. the h2c client's
     * initial request) may be logged first.
     */
    private boolean accessLogged(String prefix) {
        long deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (accessLines().any { it.startsWith(prefix) }) {
                return true
            }
            Thread.sleep(20)
        }
        false
    }

    def "a valid header sets the remote address seen by the controller, the resolver and the access log"(String name, byte[] header, String expected) {
        given:
        def server = start('micronaut.server.netty.proxy-protocol': true)

        when:
        def response = exchange(new InetSocketAddress('127.0.0.1', server.port), header)

        then:
        response.startsWith('HTTP/1.1 200')
        response.endsWith(expected)
        accessLogged(expected.split(' ')[0] + ' GET /proxy-protocol')

        cleanup:
        server.close()

        where:
        name   | header                                                        | expected
        'v1 4' | ascii('PROXY TCP4 192.0.2.10 198.51.100.1 51000 443\r\n')     | '192.0.2.10 51000 192.0.2.10'
        'v1 6' | ascii('PROXY TCP6 2001:db8::7 2001:db8::1 51001 443\r\n')     | '2001:db8:0:0:0:0:0:7 51001 2001:db8:0:0:0:0:0:7'
        'v2 4' | v2(0x21, 0x11, [192, 0, 2, 20, 198, 51, 100, 1, 0xC7, 0x38, 0x01, 0xBB]) | '192.0.2.20 51000 192.0.2.20'
        'v2 6' | v2(0x21, 0x21, ([0x20, 0x01, 0x0d, 0xb8] + [0] * 11 + [8]) + ([0x20, 0x01, 0x0d, 0xb8] + [0] * 11 + [1]) + [0xC7, 0x39, 0x01, 0xBB]) | '2001:db8:0:0:0:0:0:8 51001 2001:db8:0:0:0:0:0:8'
    }

    def "v2 LOCAL and v1 UNKNOWN keep the address of the socket"(byte[] header) {
        given:
        def server = start('micronaut.server.netty.proxy-protocol': true)

        when:
        def response = exchange(new InetSocketAddress('127.0.0.1', server.port), header)

        then:
        response.startsWith('HTTP/1.1 200')
        response.contains('\r\n\r\n127.0.0.1 ')
        accessLogged('127.0.0.1 GET /proxy-protocol')

        cleanup:
        server.close()

        where:
        header << [
                // LOCAL with an (ignored) TCP4 address block, as health checks of e.g. AWS NLB send
                v2(0x20, 0x11, [192, 0, 2, 20, 198, 51, 100, 1, 0xC7, 0x38, 0x01, 0xBB]),
                v2(0x20, 0x00, []),
                ascii('PROXY UNKNOWN\r\n'),
        ]
    }

    def "a connection without a valid header is closed without a response"(byte[] header) {
        given:
        def server = start('micronaut.server.netty.proxy-protocol': true)

        when:
        def response = exchange(new InetSocketAddress('127.0.0.1', server.port), header)

        then:
        response == ''
        accessLines().isEmpty()

        cleanup:
        server.close()

        where:
        header << [
                new byte[0], // missing: the request comes first
                ascii('PROXY TCP4 not-an-ip 198.51.100.1 51000 443\r\n'),
                ascii('PROXY TCP4 192.0.2.10 198.51.100.1 99999 443\r\n'),
                ascii('X-Forwarded-For: 192.0.2.10\r\n'),
                v2(0x31, 0x11, [192, 0, 2, 20, 198, 51, 100, 1, 0xC7, 0x38, 0x01, 0xBB]), // bad version
                v2(0x21, 0x11, [192, 0, 2, 20]), // address block too short
        ]
    }

    def "a connection that does not send the header in time is closed"() {
        given:
        def server = start('micronaut.server.netty.proxy-protocol': true, 'micronaut.server.netty.proxy-protocol-timeout': '200ms')

        when:
        def socket = SocketChannel.open(new InetSocketAddress('127.0.0.1', server.port))
        socket.socket().soTimeout = 5000
        def read = Channels.newInputStream(socket).read()

        then:
        read == -1

        cleanup:
        socket?.close()
        server.close()
    }

    def "the PROXY protocol is off by default, so a header is not accepted"() {
        given:
        def server = start([:])

        when:
        def response = exchange(new InetSocketAddress('127.0.0.1', server.port), ascii('PROXY TCP4 192.0.2.10 198.51.100.1 51000 443\r\n'))

        then:
        !response.contains('192.0.2.10')

        cleanup:
        server.close()
    }

    def "an explicit listener enables it on its own"() {
        given:
        int plain = SocketUtils.findAvailableTcpPort()
        int proxied = SocketUtils.findAvailableTcpPort()
        def server = start([
                'micronaut.server.netty.listeners.plain.port'          : plain,
                'micronaut.server.netty.listeners.proxied.port'        : proxied,
                'micronaut.server.netty.listeners.proxied.proxy-protocol': true,
        ])

        expect:
        exchange(new InetSocketAddress('127.0.0.1', plain), new byte[0]).endsWith('127.0.0.1')
        exchange(new InetSocketAddress('127.0.0.1', proxied), new byte[0]) == ''
        exchange(new InetSocketAddress('127.0.0.1', proxied), ascii('PROXY TCP4 192.0.2.10 198.51.100.1 51000 443\r\n')).endsWith('192.0.2.10 51000 192.0.2.10')

        cleanup:
        server.close()
    }

    def "a UNIX domain socket listener"() {
        given:
        Path socketPath = Files.createTempDirectory('proxy-protocol').resolve('s.sock')
        def server = start([
                'micronaut.server.netty.listeners.unix.family'        : 'UNIX',
                'micronaut.server.netty.listeners.unix.path'          : socketPath.toString(),
                'micronaut.server.netty.listeners.unix.proxy-protocol': true,
        ])

        expect:
        exchange(UnixDomainSocketAddress.of(socketPath), ascii('PROXY TCP4 192.0.2.10 198.51.100.1 51000 443\r\n')).endsWith('192.0.2.10 51000 192.0.2.10')
        exchange(UnixDomainSocketAddress.of(socketPath), new byte[0]) == ''

        cleanup:
        server.close()
    }

    def "h2c and TLS with ALPN behind a proxy that sends the header"(Map<String, Object> props, String scheme, HttpVersion version) {
        given:
        def server = start(['micronaut.server.netty.proxy-protocol': true] + props)
        def proxy = new HeaderProxy(new InetSocketAddress('127.0.0.1', server.port),
                v2(0x21, 0x11, [192, 0, 2, 30, 198, 51, 100, 1, 0xC7, 0x38, 0x01, 0xBB]))
        def client = server.applicationContext.createBean(HttpClient, new URL("$scheme://localhost:${proxy.port}"))

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET('/proxy-protocol'), String)

        then:
        response.body() == '192.0.2.30 51000 192.0.2.30'
        accessLogged('192.0.2.30 GET /proxy-protocol')

        cleanup:
        client?.close()
        proxy?.close()
        server.close()

        where:
        props                                                                                                                                  | scheme  | version
        ['micronaut.server.http-version': '2.0', 'micronaut.http.client.plaintext-mode': 'h2c_prior_knowledge', 'micronaut.http.client.http-version': '2.0'] | 'http'  | HttpVersion.HTTP_2_0
        ['micronaut.server.http-version': '2.0', 'micronaut.http.client.plaintext-mode': 'h2c', 'micronaut.http.client.http-version': '2.0']                 | 'http'  | HttpVersion.HTTP_2_0
        ['micronaut.server.http-version': '2.0', 'micronaut.server.ssl.enabled': true, 'micronaut.server.ssl.build-self-signed': true, 'micronaut.server.ssl.port': -1, 'micronaut.http.client.ssl.insecure-trust-all-certificates': true, 'micronaut.http.client.http-version': '2.0'] | 'https' | HttpVersion.HTTP_2_0
        ['micronaut.server.ssl.enabled': true, 'micronaut.server.ssl.build-self-signed': true, 'micronaut.server.ssl.port': -1, 'micronaut.http.client.ssl.insecure-trust-all-certificates': true] | 'https' | HttpVersion.HTTP_1_1
    }

    def "a QUIC listener with the PROXY protocol fails to start"() {
        when:
        start([
                'micronaut.server.ssl.enabled'                        : true,
                'micronaut.server.ssl.build-self-signed'              : true,
                'micronaut.server.netty.listeners.quic.family'        : 'QUIC',
                'micronaut.server.netty.listeners.quic.port'          : -1,
                'micronaut.server.netty.listeners.quic.proxy-protocol': true,
        ])

        then:
        def e = thrown(Exception)
        rootMessages(e).any { it?.contains('PROXY protocol is not supported for QUIC') }
    }

    private static List<String> rootMessages(Throwable t) {
        List<String> messages = []
        for (Throwable c = t; c != null; c = c.cause) {
            messages << c.message
        }
        messages
    }

    static byte[] ascii(String s) {
        s.getBytes(StandardCharsets.US_ASCII)
    }

    static byte[] v2(int verCmd, int famProto, List<Integer> addresses) {
        def out = new ByteArrayOutputStream()
        out.write(V2_SIGNATURE)
        out.write(verCmd)
        out.write(famProto)
        out.write((addresses.size() >> 8) & 0xFF)
        out.write(addresses.size() & 0xFF)
        addresses.each { out.write(it & 0xFF) }
        out.toByteArray()
    }

    /**
     * Send the header and a request, and read until the server closes the connection.
     *
     * @return The response, or an empty string if the server closed the connection without one
     */
    static String exchange(SocketAddress address, byte[] header) {
        try (SocketChannel socket = SocketChannel.open(address)) {
            // the server closes the connection after the response, or right away; a UNIX domain
            // socket channel has no Socket view to set a read timeout on
            def out = Channels.newOutputStream(socket)
            out.write(header)
            out.write(ascii("GET /proxy-protocol HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"))
            out.flush()
            try {
                return new String(Channels.newInputStream(socket).readAllBytes(), StandardCharsets.ISO_8859_1)
            } catch (IOException ignored) {
                // a reset counts as closed
                return ''
            }
        }
    }

    /**
     * A TCP proxy that sends a fixed PROXY protocol header to the server ahead of each connection.
     */
    static class HeaderProxy implements Closeable {
        final java.nio.channels.ServerSocketChannel server = java.nio.channels.ServerSocketChannel.open().bind(new InetSocketAddress('127.0.0.1', 0))
        final List<Closeable> open = new CopyOnWriteArrayList<>()

        HeaderProxy(InetSocketAddress target, byte[] header) {
            Thread.startDaemon('header-proxy') {
                while (server.isOpen()) {
                    SocketChannel client
                    try {
                        client = server.accept()
                    } catch (IOException ignored) {
                        return
                    }
                    SocketChannel upstream = SocketChannel.open(target)
                    open << client << upstream
                    upstream.write(ByteBuffer.wrap(header))
                    pipe(client, upstream)
                    pipe(upstream, client)
                }
            }
        }

        int getPort() {
            ((InetSocketAddress) server.localAddress).port
        }

        private static void pipe(SocketChannel from, SocketChannel to) {
            Thread.startDaemon('header-proxy-pipe') {
                ByteBuffer buf = ByteBuffer.allocate(16384)
                try {
                    while (from.read(buf) >= 0) {
                        buf.flip()
                        while (buf.hasRemaining()) {
                            to.write(buf)
                        }
                        buf.clear()
                    }
                } catch (IOException ignored) {
                } finally {
                    try { to.shutdownOutput() } catch (IOException ignored) { }
                }
            }
        }

        @Override
        void close() {
            server.close()
            open.each { try { it.close() } catch (IOException ignored) { } }
        }
    }

    @Requires(property = 'spec.name', value = 'ProxyProtocolSpec')
    @Controller('/proxy-protocol')
    static class AddressController {
        private final HttpClientAddressResolver resolver

        AddressController(HttpClientAddressResolver resolver) {
            this.resolver = resolver
        }

        @Get
        String get(HttpRequest<?> request) {
            def remote = request.remoteAddress
            "${remote.address.hostAddress} ${remote.port} ${resolver.resolve(request)}"
        }
    }
}
