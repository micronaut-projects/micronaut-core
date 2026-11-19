package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.ssl.CertificateProvider
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.pkitesting.CertificateBuilder
import io.netty.pkitesting.X509Bundle
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Sinks
import spock.lang.Specification

import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.X509Certificate

class SniSpec extends Specification {

    static X509Bundle bundle(String cn) {
        new CertificateBuilder().subject("CN=" + cn).setIsCertificateAuthority(true).buildSelfSigned()
    }

    static String pem(X509Bundle b) {
        "string:" + b.privateKeyPEM + b.certificatePathPEM
    }

    EmbeddedServer start(Map<String, Object> extra = [:]) {
        Map<String, Object> config = [
            'spec.name'                                         : 'SniSpec',
            'micronaut.ssl.enabled'                             : true,
            'micronaut.server.ssl.enabled'                      : true,
            'micronaut.certificate.resource.default.resource'   : pem(bundle("default")),
            'micronaut.certificate.resource.a.resource'         : pem(bundle("a")),
            'micronaut.certificate.resource.b.resource'         : pem(bundle("b")),
            'micronaut.certificate.resource.wild.resource'      : pem(bundle("wild")),
            'micronaut.server.netty.listeners.tls.port'         : -1,
            'micronaut.server.netty.listeners.tls.ssl'          : true,
            'micronaut.server.netty.listeners.tls.key-name'     : 'default',
            'micronaut.server.netty.listeners.tls.sni-key-names': [
                'a.example.com': 'a',
                'B.Example.com': 'b',
                '*.wild.test'  : 'wild',
            ],
        ]
        config.putAll(extra)
        ApplicationContext.run(EmbeddedServer, config)
    }

    def 'two host names are served two certificates and an unknown one the default'() {
        given:
        def server = start()

        expect:
        handshake(server, "a.example.com").subject == "CN=a"
        handshake(server, "b.example.com").subject == "CN=b"
        handshake(server, "c.example.com").subject == "CN=default"
        handshake(server, null).subject == "CN=default"

        cleanup:
        server.close()
    }

    def 'a wildcard matches exactly one label'() {
        given:
        def server = start()

        expect:
        handshake(server, "x.wild.test").subject == "CN=wild"
        handshake(server, "y.x.wild.test").subject == "CN=default"
        handshake(server, "wild.test").subject == "CN=default"

        cleanup:
        server.close()
    }

    def 'requests are served after the selection, and h2 is negotiated through ALPN per host'() {
        given:
        def server = start(['micronaut.server.http-version': '2.0'])

        when:
        def a = handshake(server, "a.example.com", ["h2", "http/1.1"])
        def unknown = handshake(server, "other.test", ["h2", "http/1.1"])
        def http1 = handshake(server, "b.example.com", ["http/1.1"], "GET /sni HTTP/1.1\r\nHost: b.example.com\r\nConnection: close\r\n\r\n")

        then:
        a.subject == "CN=a"
        a.protocol == "h2"
        unknown.subject == "CN=default"
        unknown.protocol == "h2"
        http1.subject == "CN=b"
        http1.protocol == "http/1.1"
        http1.response.startsWith("HTTP/1.1 200")
        http1.response.endsWith("secure=true")

        cleanup:
        server.close()
    }

    def 'strict mode answers a request for another host with 421'() {
        given:
        def server = start(['micronaut.server.netty.listeners.tls.sni-strict': true])

        expect:
        request(server, "a.example.com", "a.example.com:443").startsWith("HTTP/1.1 200")
        request(server, "A.EXAMPLE.COM", "a.example.com").startsWith("HTTP/1.1 200")
        request(server, "a.example.com", "b.example.com").startsWith("HTTP/1.1 421")
        // without a server name there is nothing to compare against
        request(server, null, "b.example.com").startsWith("HTTP/1.1 200")

        cleanup:
        server.close()
    }

    def 'without strict mode a request for another host is served'() {
        given:
        def server = start()

        expect:
        request(server, "a.example.com", "b.example.com").startsWith("HTTP/1.1 200")

        cleanup:
        server.close()
    }

    def 'each host certificate is refreshed independently'() {
        given:
        def server = start([
            'micronaut.server.ssl.key.password'                         : '',
            'micronaut.server.netty.listeners.tls.sni-key-names'         : ['a.example.com': 'a', 'dyn.test': 'dynamic'],
        ])
        def provider = server.applicationContext.getBean(DynamicProvider)

        expect:
        handshake(server, "dyn.test").subject == "CN=dynamic-1"

        when:
        provider.emit(bundle("dynamic-2"))

        then:
        handshake(server, "dyn.test").subject == "CN=dynamic-2"
        handshake(server, "a.example.com").subject == "CN=a"
        handshake(server, "unknown.test").subject == "CN=default"

        cleanup:
        server.close()
    }

    def 'invalid host names are rejected'() {
        when:
        start(['micronaut.server.netty.listeners.tls.sni-key-names': ['a.*.example.com': 'a']])

        then:
        def e = thrown(Exception)
        rootCause(e).message.contains("Invalid SNI host name")
    }

    private static Throwable rootCause(Throwable t) {
        while (t.cause != null && t.cause != t) {
            if (t.message?.contains("Invalid SNI host name")) {
                return t
            }
            t = t.cause
        }
        return t
    }

    private static String request(EmbeddedServer server, String serverName, String host) {
        handshake(server, serverName, null, "GET /sni HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n").response
    }

    private static Map<String, String> handshake(EmbeddedServer server, String serverName, List<String> alpn = null, String request = null) {
        SSLContext sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, [new X509TrustManager() {
            X509Certificate[] getAcceptedIssuers() { new X509Certificate[0] }

            void checkClientTrusted(X509Certificate[] chain, String authType) {}

            void checkServerTrusted(X509Certificate[] chain, String authType) {}
        }] as TrustManager[], null)
        try (SSLSocket socket = (SSLSocket) sslContext.socketFactory.createSocket("127.0.0.1", server.port)) {
            socket.soTimeout = 10_000
            SSLParameters parameters = socket.SSLParameters
            parameters.serverNames = serverName == null ? [] : [new SNIHostName(serverName)]
            if (alpn != null) {
                parameters.applicationProtocols = alpn as String[]
            }
            socket.SSLParameters = parameters
            socket.startHandshake()
            Map<String, String> result = [
                subject : ((X509Certificate) socket.session.peerCertificates[0]).subjectX500Principal.name,
                protocol: socket.applicationProtocol,
            ]
            if (request != null) {
                socket.outputStream.write(request.getBytes(StandardCharsets.US_ASCII))
                socket.outputStream.flush()
                result.response = new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
            }
            return result
        }
    }

    @Requires(property = "spec.name", value = "SniSpec")
    @Controller("/sni")
    static class SniController {
        @Get
        String index(io.micronaut.http.HttpRequest<?> request) {
            "secure=" + request.secure
        }
    }

    @Requires(property = "spec.name", value = "SniSpec")
    @Singleton
    @Named("dynamic")
    static class DynamicProvider implements CertificateProvider {
        final Sinks.Many<KeyStore> sink = Sinks.many().replay().latest()

        DynamicProvider() {
            emit(bundle("dynamic-1"))
        }

        void emit(X509Bundle b) {
            KeyStore ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("key", b.keyPair.private, new char[0], b.certificatePath)
            sink.tryEmitNext(ks)
        }

        @Override
        Publisher<KeyStore> getKeyStore() {
            sink.asFlux()
        }

        @Override
        String getName() {
            "dynamic"
        }
    }
}
