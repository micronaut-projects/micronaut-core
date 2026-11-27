package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.watch.ReloadingConfigurationWatcher
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.ssl.CertificateProvider
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.http3.Http3
import io.netty.handler.codec.http3.Http3ClientConnectionHandler
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.pkitesting.CertificateBuilder
import io.netty.pkitesting.X509Bundle
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Specification


import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

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
        // the given map backs the configuration, so a test can change it and refresh
        config.each { k, v ->
            if (!extra.containsKey(k)) {
                extra[k] = v
            }
        }
        ApplicationContext.run(EmbeddedServer, extra)
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

    def 'a request for another host than the server name is served'() {
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

    def 'HTTP/3 selects the certificate by server name'() {
        given:
        def server = start([
            'micronaut.server.netty.listeners.tls.family': 'QUIC',
            'micronaut.server.netty.listeners.tls.ssl'   : false,
        ])
        def loop = new NioEventLoopGroup(1)

        expect:
        quicSubject(loop, server, "a.example.com") == "CN=a"
        quicSubject(loop, server, "x.wild.test") == "CN=wild"
        quicSubject(loop, server, "unknown.test") == "CN=default"

        cleanup:
        loop.shutdownGracefully()
        server.close()
    }

    private static String quicSubject(NioEventLoopGroup loop, EmbeddedServer server, String serverName) {
        def clientContext = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(Http3.supportedApplicationProtocols()).build()
        def ch = new Bootstrap()
            .group(loop)
            .channel(NioDatagramChannel)
            .handler(Http3.newQuicClientCodecBuilder()
                .sslEngineProvider { QuicChannel q -> clientContext.newEngine(q.alloc(), serverName, server.port) }
                .build())
            .bind(0).sync().channel()
        try {
            QuicChannel qc = QuicChannel.newBootstrap(ch)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel c) throws Exception {
                        c.pipeline().addLast(new Http3ClientConnectionHandler())
                    }
                })
                .remoteAddress(new InetSocketAddress('127.0.0.1', server.port))
                .connect().get(10, TimeUnit.SECONDS)
            try {
                return ((X509Certificate) qc.sslEngine().session.peerCertificates[0]).subjectX500Principal.name
            } finally {
                qc.close().sync()
            }
        } finally {
            ch.close().sync()
        }
    }

    def 'host certificates trust the same clients as a legacy default certificate'() {
        given: "a legacy key store and trust store that require a client certificate"
        X509Bundle serverBundle = bundle("legacy")
        X509Bundle clientBundle = bundle("client")
        Path dir = Files.createTempDirectory("sni-trust")
        Path trust = dir.resolve("trust.p12")
        KeyStore trustStore = KeyStore.getInstance("PKCS12")
        trustStore.load(null, null)
        trustStore.setCertificateEntry("client", clientBundle.certificate)
        try (OutputStream out = Files.newOutputStream(trust)) {
            trustStore.store(out, "secret".toCharArray())
        }
        def server = start([
            'micronaut.server.netty.listeners.tls.key-name'        : null,
            'micronaut.server.ssl.key-store.key-path'              : "string:" + serverBundle.privateKeyPEM,
            'micronaut.server.ssl.key-store.certificate-path'      : "string:" + serverBundle.certificatePathPEM,
            'micronaut.server.ssl.trust-store.path'                : "file:" + trust,
            'micronaut.server.ssl.trust-store.password'            : "secret",
            'micronaut.server.ssl.trust-store.type'                : "PKCS12",
            'micronaut.server.ssl.client-authentication'           : "need",
        ])

        expect: "the client certificate is accepted for the default and for a host certificate"
        handshake(server, "unknown.test", null, "GET /sni HTTP/1.1\r\nHost: unknown.test\r\nConnection: close\r\n\r\n", clientBundle).response.startsWith("HTTP/1.1 200")
        def a = handshake(server, "a.example.com", null, "GET /sni HTTP/1.1\r\nHost: a.example.com\r\nConnection: close\r\n\r\n", clientBundle)
        a.subject == "CN=a"
        a.response.startsWith("HTTP/1.1 200")

        cleanup:
        server?.close()
        trust.toFile().delete()
        dir.toFile().delete()
    }

    def 'HTTP/3 selects host certificates next to a legacy default certificate'() {
        given:
        def server = start([
            'micronaut.server.netty.listeners.tls.family'  : 'QUIC',
            'micronaut.server.netty.listeners.tls.ssl'     : false,
            'micronaut.server.netty.listeners.tls.key-name': null,
            'micronaut.server.ssl.build-self-signed'       : true,
        ])
        def loop = new NioEventLoopGroup(1)

        expect:
        quicSubject(loop, server, "a.example.com") == "CN=a"
        quicSubject(loop, server, "unknown.test") != "CN=a"

        cleanup:
        loop.shutdownGracefully()
        server.close()
    }

    def 'a provided SSLContext cannot be combined with host certificates'() {
        when:
        start(['micronaut.server.ssl.context-name': 'provided'])

        then:
        def e = thrown(Exception)
        causes(e).any { it.message?.contains("cannot be combined with a provided SSLContext") }
    }

    def 'a legacy key store keeps serving when a global trust-name is set'() {
        given:
        X509Bundle legacy = bundle("legacy")
        def server = start([
            'micronaut.server.netty.listeners.tls.key-name'     : null,
            'micronaut.server.netty.listeners.tls.sni-key-names': [:],
            'micronaut.server.ssl.trust-name'                   : 'a',
            'micronaut.server.ssl.key-store.key-path'           : "string:" + legacy.privateKeyPEM,
            'micronaut.server.ssl.key-store.certificate-path'   : "string:" + legacy.certificatePathPEM,
        ])

        expect:
        handshake(server, "unknown.test").subject == "CN=legacy"

        cleanup:
        server.close()
    }

    def 'a host certificate does not use the password of a legacy key store'() {
        given: "a password protected legacy key store and a PEM host certificate"
        X509Bundle legacy = bundle("legacy")
        Path dir = Files.createTempDirectory("sni-password")
        Path store = dir.resolve("legacy.p12")
        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("key", legacy.keyPair.private, "secret".toCharArray(), legacy.certificatePath)
        try (OutputStream out = Files.newOutputStream(store)) {
            ks.store(out, "secret".toCharArray())
        }
        def server = start([
            'micronaut.server.netty.listeners.tls.key-name': null,
            'micronaut.server.ssl.key-store.path'          : "file:" + store,
            'micronaut.server.ssl.key-store.password'      : "secret",
            'micronaut.server.ssl.key-store.type'          : "PKCS12",
        ])

        expect:
        handshake(server, "unknown.test").subject == "CN=legacy"
        handshake(server, "a.example.com").subject == "CN=a"

        cleanup:
        server?.close()
        store.toFile().delete()
        dir.toFile().delete()
    }

    def 'a host keeps its certificate across a refresh until the new one is built'() {
        given:
        Map<String, Object> values = [
            'micronaut.server.ssl.key.password'                 : '',
            'micronaut.server.netty.listeners.tls.sni-key-names': ['dyn.test': 'dynamic'],
        ]
        def server = start(values)
        def provider = server.applicationContext.getBean(DynamicProvider)

        expect:
        handshake(server, "dyn.test").subject == "CN=dynamic-1"

        when: "the provider stops emitting and an SSL change rebuilds the listener in place"
        provider.pending = true
        values['micronaut.server.ssl.handshake-timeout'] = '9s'
        def result = server.applicationContext.getBean(ConfigurationRefresher).refresh()

        then: "the host keeps the certificate it had"
        result.outcomes() == [ReloadingConfigurationWatcher.Outcome.APPLIED]
        handshake(server, "dyn.test").subject == "CN=dynamic-1"

        cleanup:
        server.close()
    }

    private static List<Throwable> causes(Throwable t) {
        List<Throwable> all = []
        List<Throwable> todo = [t]
        while (!todo.isEmpty()) {
            Throwable next = todo.remove(0)
            if (next != null && !all.contains(next)) {
                all << next
                todo << next.cause
                todo.addAll(next.suppressed)
            }
        }
        return all
    }

    def 'host certificates are selected after a PROXY protocol header'() {
        given:
        def server = start(['micronaut.server.netty.listeners.tls.proxy-protocol': true])

        expect:
        proxiedSubject(server, "a.example.com") == "CN=a"
        proxiedSubject(server, "x.wild.test") == "CN=wild"
        proxiedSubject(server, "unknown.test") == "CN=default"

        cleanup:
        server.close()
    }

    private static String proxiedSubject(EmbeddedServer server, String serverName) {
        SSLContext sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, [new X509TrustManager() {
            X509Certificate[] getAcceptedIssuers() { new X509Certificate[0] }

            void checkClientTrusted(X509Certificate[] chain, String authType) {}

            void checkServerTrusted(X509Certificate[] chain, String authType) {}
        }] as TrustManager[], null)
        try (Socket plain = new Socket("127.0.0.1", server.port)) {
            plain.soTimeout = 10_000
            plain.outputStream.write("PROXY TCP4 192.0.2.10 198.51.100.1 51000 443\r\n".getBytes(StandardCharsets.US_ASCII))
            plain.outputStream.flush()
            try (SSLSocket socket = (SSLSocket) sslContext.socketFactory.createSocket(plain, "127.0.0.1", server.port, false)) {
                SSLParameters parameters = socket.SSLParameters
                parameters.serverNames = [new SNIHostName(serverName)]
                socket.SSLParameters = parameters
                socket.startHandshake()
                return ((X509Certificate) socket.session.peerCertificates[0]).subjectX500Principal.name
            }
        }
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

    private static Map<String, String> handshake(EmbeddedServer server, String serverName, List<String> alpn = null, String request = null, X509Bundle clientCertificate = null) {
        SSLContext sslContext = SSLContext.getInstance("TLS")
        KeyManager[] keyManagers = null
        if (clientCertificate != null) {
            KeyStore ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("client", clientCertificate.keyPair.private, "secret".toCharArray(), clientCertificate.certificatePath)
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
            kmf.init(ks, "secret".toCharArray())
            keyManagers = kmf.keyManagers
        }
        sslContext.init(keyManagers, [new X509TrustManager() {
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
        volatile boolean pending

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
            // a pending provider has not produced its key store yet
            pending ? Flux.never() : sink.asFlux()
        }

        @Override
        String getName() {
            "dynamic"
        }
    }
}
