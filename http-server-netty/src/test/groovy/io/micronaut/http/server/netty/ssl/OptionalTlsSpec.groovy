package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.util.HttpHostResolver
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate

class OptionalTlsSpec extends Specification {

    EmbeddedServer start(Map<String, Object> extra = [:]) {
        ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                    : 'OptionalTlsSpec',
            'micronaut.server.ssl.enabled'                 : true,
            'micronaut.server.ssl.build-self-signed'       : true,
            'micronaut.server.netty.listeners.both.port'   : -1,
            'micronaut.server.netty.listeners.both.ssl'    : true,
            'micronaut.server.netty.listeners.both.ssl-optional': true,
        ] + extra)
    }

    def 'one port accepts cleartext and TLS, and each reports its own scheme'() {
        given:
        def server = start()

        when:
        def plain = cleartext(server, "GET /optional-tls HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n")
        def tls = secure(server, null, "GET /optional-tls HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n").response

        then:
        plain.startsWith("HTTP/1.1 200")
        plain.endsWith("secure=false host=http://example.com")
        tls.startsWith("HTTP/1.1 200")
        tls.endsWith("secure=true host=https://example.com")

        cleanup:
        server.close()
    }

    def 'h2 over TLS via ALPN and h2c prior knowledge share the port'() {
        given:
        def server = start(['micronaut.server.http-version': '2.0'])

        when:
        def tls = secure(server, ["h2", "http/1.1"], null)
        def plain = cleartext(server, "GET /optional-tls HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n")
        def preface = cleartextBytes(server, "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n" as String, 9)

        then:
        tls.protocol == "h2"
        plain.endsWith("secure=false host=http://example.com")
        // the server answers the connection preface with a SETTINGS frame (type 4)
        preface[3] == 4 as byte

        cleanup:
        server.close()
    }

    def 'cleartext on an optional TLS listener is served, not redirected, in dual-protocol mode'() {
        given:
        def server = start([
            'micronaut.server.dual-protocol'         : true,
            'micronaut.server.http-to-https-redirect': true,
        ])

        expect:
        cleartext(server, "GET /optional-tls HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n").startsWith("HTTP/1.1 200")

        cleanup:
        server.close()
    }

    def 'a connection that never sends enough to detect the protocol is closed after the handshake timeout'() {
        given:
        def server = start(['micronaut.server.ssl.handshake-timeout': '500ms'])

        when:
        long begin = System.nanoTime()
        int read
        try (Socket socket = new Socket("127.0.0.1", server.port)) {
            socket.soTimeout = 10_000
            socket.outputStream.write("GE".getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            read = socket.inputStream.read()
        }
        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000

        then: "the server closed the connection instead of waiting for the read timeout"
        read == -1
        elapsedMillis < 5_000

        cleanup:
        server.close()
    }

    def 'without the option the TLS listener rejects cleartext'() {
        given:
        def server = start(['micronaut.server.netty.listeners.both.ssl-optional': false])

        expect:
        !cleartext(server, "GET /optional-tls HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n").startsWith("HTTP/1.1 200")

        cleanup:
        server.close()
    }

    private static String cleartext(EmbeddedServer server, String request) {
        try (Socket socket = new Socket("127.0.0.1", server.port)) {
            socket.soTimeout = 10_000
            socket.outputStream.write(request.getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            try {
                return new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
            } catch (IOException ignored) {
                return ""
            }
        }
    }

    private static byte[] cleartextBytes(EmbeddedServer server, String request, int n) {
        try (Socket socket = new Socket("127.0.0.1", server.port)) {
            socket.soTimeout = 10_000
            socket.outputStream.write(request.getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            return socket.inputStream.readNBytes(n)
        }
    }

    private static Map<String, String> secure(EmbeddedServer server, List<String> alpn, String request) {
        SSLContext sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, [new X509TrustManager() {
            X509Certificate[] getAcceptedIssuers() { new X509Certificate[0] }

            void checkClientTrusted(X509Certificate[] chain, String authType) {}

            void checkServerTrusted(X509Certificate[] chain, String authType) {}
        }] as TrustManager[], null)
        try (SSLSocket socket = (SSLSocket) sslContext.socketFactory.createSocket("127.0.0.1", server.port)) {
            socket.soTimeout = 10_000
            if (alpn != null) {
                SSLParameters parameters = socket.SSLParameters
                parameters.applicationProtocols = alpn as String[]
                socket.SSLParameters = parameters
            }
            socket.startHandshake()
            Map<String, String> result = [protocol: socket.applicationProtocol]
            if (request != null) {
                socket.outputStream.write(request.getBytes(StandardCharsets.US_ASCII))
                socket.outputStream.flush()
                result.response = new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
            }
            return result
        }
    }

    @Requires(property = "spec.name", value = "OptionalTlsSpec")
    @Controller("/optional-tls")
    static class OptionalTlsController {
        private final HttpHostResolver hostResolver

        OptionalTlsController(HttpHostResolver hostResolver) {
            this.hostResolver = hostResolver
        }

        @Get
        String index(HttpRequest<?> request) {
            "secure=" + request.secure + " host=" + hostResolver.resolve(request)
        }
    }
}
