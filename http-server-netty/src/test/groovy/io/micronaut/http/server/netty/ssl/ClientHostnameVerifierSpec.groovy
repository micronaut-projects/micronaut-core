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
import io.netty.handler.ssl.util.SelfSignedCertificate
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

class ClientHostnameVerifierSpec extends Specification {

    @Shared
    SelfSignedCertificate serverCert = new SelfSignedCertificate("server.example.com")

    @Shared
    @AutoCleanup
    ApplicationContext ctx

    @Shared
    EmbeddedServer server

    def setupSpec() {
        ctx = ApplicationContext.builder()
                .properties([
                        'spec.name'                        : 'ClientHostnameVerifierSpec',
                        'micronaut.server.http-version'    : '2.0',
                        'micronaut.server.ssl.enabled'     : true,
                        'micronaut.server.ssl.port'        : -1,
                        'micronaut.server.ssl.context-name': 'verifier',
                ])
                .build()
        ctx.registerSingleton(SSLContext, sslContext(serverCert, true), Qualifiers.byName('verifier'))
        ctx.start()
        server = ctx.getBean(EmbeddedServer)
        server.start()
    }

    def 'a verifier accepts a host the endpoint identification rejects'(HttpVersion version, String expected) {
        given:
        List<String> hosts = []
        def client = client(version, { String host, SSLSession session ->
            hosts << host
            (session.peerCertificates[0] as X509Certificate).subjectX500Principal.name == 'CN=server.example.com'
        })

        when:
        def body = client.toBlocking().retrieve('/hostname-verifier')

        then:
        body == expected
        hosts == ['localhost']

        cleanup:
        client.close()

        where:
        version             | expected
        HttpVersion.HTTP_1_1 | 'HTTP_1_1'
        HttpVersion.HTTP_2_0 | 'HTTP_2_0'
    }

    def 'a rejecting verifier fails the connection'(HttpVersion version) {
        given:
        def client = client(version, { String host, SSLSession session -> false })

        when:
        client.toBlocking().retrieve('/hostname-verifier')

        then:
        def e = thrown(Exception)
        causes(e).any { it instanceof SSLPeerUnverifiedException && it.message.contains('localhost') }

        cleanup:
        client.close()

        where:
        version << [HttpVersion.HTTP_1_1, HttpVersion.HTTP_2_0]
    }

    def 'a throwing verifier fails the connection'() {
        given:
        def client = client(HttpVersion.HTTP_2_0, { String host, SSLSession session -> throw new IllegalStateException('boom') })

        when:
        client.toBlocking().retrieve('/hostname-verifier')

        then:
        def e = thrown(Exception)
        causes(e).any { it instanceof SSLPeerUnverifiedException && it.cause instanceof IllegalStateException }

        cleanup:
        client.close()
    }

    private static List<Throwable> causes(Throwable e) {
        def causes = []
        for (Throwable t = e; t != null; t = t.cause) {
            causes << t
        }
        return causes
    }

    private HttpClient client(HttpVersion version, HostnameVerifier verifier) {
        // tag::hostnameVerifier[]
        def configuration = new DefaultHttpClientConfiguration()
        configuration.httpVersion = version
        def ssl = new ClientSslConfiguration()
        // the server certificate names server.example.com, not localhost
        ssl.sslContext = sslContext(serverCert, false)
        ssl.endpointIdentification = false
        ssl.hostnameVerifier = verifier
        configuration.sslConfiguration = ssl
        return HttpClient.create(new URL("https://localhost:$server.port"), configuration)
        // end::hostnameVerifier[]
    }

    private static SSLContext sslContext(SelfSignedCertificate cert, boolean server) {
        KeyManagerFactory kmf = null
        if (server) {
            KeyStore ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("key", cert.key(), "secret".toCharArray(), new Certificate[]{cert.cert()})
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, "secret".toCharArray())
        }
        KeyStore ts = KeyStore.getInstance("PKCS12")
        ts.load(null, null)
        ts.setCertificateEntry("cert", cert.cert())
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ts)

        SSLContext context = SSLContext.getInstance("TLS")
        context.init(kmf?.keyManagers, tmf.trustManagers, null)
        return context
    }

    @Controller
    @Requires(property = 'spec.name', value = 'ClientHostnameVerifierSpec')
    static class TestController {
        @Get('/hostname-verifier')
        String get(HttpRequest<?> request) {
            request.httpVersion.toString()
        }
    }
}
