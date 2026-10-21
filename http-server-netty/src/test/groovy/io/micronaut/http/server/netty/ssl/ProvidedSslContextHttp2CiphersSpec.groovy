package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.ClientSslConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.util.SelfSignedCertificate
import spock.lang.Shared
import spock.lang.Specification

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.cert.Certificate

class ProvidedSslContextHttp2CiphersSpec extends Specification {

    // in the default Netty and JDK cipher suites, but forbidden by HTTP/2 (RFC 7540 9.2.2)
    static final String CBC_CIPHER = 'TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA'
    static final String GCM_CIPHER = 'TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256'

    @Shared
    SelfSignedCertificate serverCert = new SelfSignedCertificate("localhost", "RSA", 2048)

    def 'a provided server SSLContext keeps the HTTP/2 cipher suites'() {
        given:
        def ctx = startServer([:])
        def server = ctx.getBean(EmbeddedServer)

        expect:
        !Http2SecurityUtil.CIPHERS.contains(CBC_CIPHER)
        handshake(server.port, CBC_CIPHER) == null
        handshake(server.port, GCM_CIPHER) == GCM_CIPHER

        cleanup:
        ctx.close()
    }

    def 'configured ciphers are used with a provided server SSLContext'() {
        given:
        def ctx = startServer(['micronaut.server.ssl.ciphers': GCM_CIPHER])
        def server = ctx.getBean(EmbeddedServer)

        expect:
        handshake(server.port, GCM_CIPHER) == GCM_CIPHER
        handshake(server.port, 'TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384') == null

        cleanup:
        ctx.close()
    }

    def 'HTTP/2 over ALPN works with provided SSLContexts'() {
        given:
        def ctx = startServer([:])
        def server = ctx.getBean(EmbeddedServer)
        def client = client(server.port)

        expect:
        client.toBlocking().retrieve('/provided-ciphers') == 'HTTP_2_0'

        cleanup:
        client.close()
        ctx.close()
    }

    def 'a provided client SSLContext keeps the HTTP/2 cipher suites'() {
        given: 'a server that only accepts a cipher HTTP/2 forbids'
        def ctx = startServer([
                'micronaut.server.ssl.ciphers'  : CBC_CIPHER,
                'micronaut.server.ssl.protocols': 'TLSv1.2',
        ])
        def server = ctx.getBean(EmbeddedServer)
        def client = client(server.port)

        when:
        client.toBlocking().retrieve('/provided-ciphers')

        then:
        thrown(Exception)

        cleanup:
        client.close()
        ctx.close()
    }

    private ApplicationContext startServer(Map<String, Object> extra) {
        def ctx = ApplicationContext.builder()
                .properties([
                        'spec.name'                        : 'ProvidedSslContextHttp2CiphersSpec',
                        'micronaut.server.http-version'    : '2.0',
                        'micronaut.server.ssl.enabled'     : true,
                        'micronaut.server.ssl.port'        : -1,
                        'micronaut.server.ssl.context-name': 'provided',
                ] + extra)
                .build()
        ctx.registerSingleton(SSLContext, sslContext(), Qualifiers.byName('provided'))
        ctx.start()
        ctx.getBean(EmbeddedServer).start()
        return ctx
    }

    private HttpClient client(int port) {
        def configuration = new DefaultHttpClientConfiguration()
        configuration.httpVersion = HttpVersion.HTTP_2_0
        def ssl = new ClientSslConfiguration()
        ssl.sslContext = sslContext()
        configuration.sslConfiguration = ssl
        return HttpClient.create(new URL("https://localhost:$port"), configuration)
    }

    /**
     * @return the negotiated cipher suite, or {@code null} if the handshake failed
     */
    private String handshake(int port, String cipher) {
        SSLSocket socket = (SSLSocket) sslContext().socketFactory.createSocket('localhost', port)
        try {
            socket.enabledProtocols = ['TLSv1.2'] as String[]
            socket.enabledCipherSuites = [cipher] as String[]
            def params = socket.SSLParameters
            params.applicationProtocols = ['h2'] as String[]
            socket.SSLParameters = params
            socket.startHandshake()
            return socket.session.cipherSuite
        } catch (IOException ignored) {
            return null
        } finally {
            socket.close()
        }
    }

    private SSLContext sslContext() {
        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("key", serverCert.key(), "secret".toCharArray(), new Certificate[]{serverCert.cert()})
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, "secret".toCharArray())

        KeyStore ts = KeyStore.getInstance("PKCS12")
        ts.load(null, null)
        ts.setCertificateEntry("cert", serverCert.cert())
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ts)

        SSLContext context = SSLContext.getInstance("TLS")
        context.init(kmf.keyManagers, tmf.trustManagers, null)
        return context
    }

    @Controller
    @Requires(property = 'spec.name', value = 'ProvidedSslContextHttp2CiphersSpec')
    static class TestController {
        @Get('/provided-ciphers')
        String get(HttpRequest<?> request) {
            request.httpVersion.toString()
        }
    }
}
