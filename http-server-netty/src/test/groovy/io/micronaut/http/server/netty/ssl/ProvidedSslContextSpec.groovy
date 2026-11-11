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

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class ProvidedSslContextSpec extends Specification {

    @Shared
    SelfSignedCertificate serverCert = new SelfSignedCertificate("server.example.com")
    @Shared
    SelfSignedCertificate clientCert = new SelfSignedCertificate("client.example.com")

    @Shared
    @AutoCleanup
    ApplicationContext ctx

    @Shared
    EmbeddedServer server

    def setupSpec() {
        ctx = ApplicationContext.builder()
                .properties([
                        'spec.name'                                  : 'ProvidedSslContextSpec',
                        'micronaut.server.http-version'              : '2.0',
                        'micronaut.server.ssl.enabled'               : true,
                        'micronaut.server.ssl.port'                  : -1,
                        'micronaut.server.ssl.context-name'          : 'mtls',
                        'micronaut.server.ssl.client-authentication' : 'need',
                ])
                .build()
        ctx.registerSingleton(SSLContext, sslContext(serverCert, clientCert), Qualifiers.byName('mtls'))
        ctx.start()
        server = ctx.getBean(EmbeddedServer)
        server.start()
    }

    def 'server and client use the provided contexts, with ALPN and client auth'() {
        given:
        def client = client(false)

        when:
        def body = client.toBlocking().retrieve('/provided-ssl')

        then:
        body == 'HTTP_2_0 CN=client.example.com'

        cleanup:
        client.close()
    }

    def 'endpoint identification stays on by default'() {
        given:
        def client = client(true)

        when:
        client.toBlocking().retrieve('/provided-ssl')

        then:
        def e = thrown(Exception)
        def causes = []
        for (Throwable t = e; t != null; t = t.cause) {
            causes << t
        }
        causes.any { it instanceof CertificateException && it.message.contains('No name matching localhost') }

        cleanup:
        client.close()
    }

    def 'the server needs a client certificate'() {
        given:
        def configuration = new DefaultHttpClientConfiguration()
        configuration.httpVersion = HttpVersion.HTTP_2_0
        def ssl = new ClientSslConfiguration()
        ssl.sslContext = sslContext(null, serverCert)
        ssl.endpointIdentification = false
        configuration.sslConfiguration = ssl
        def client = HttpClient.create(new URL("https://localhost:$server.port"), configuration)

        when:
        client.toBlocking().retrieve('/provided-ssl')

        then:
        thrown(Exception)

        cleanup:
        client.close()
    }

    private HttpClient client(boolean endpointIdentification) {
        def configuration = new DefaultHttpClientConfiguration()
        configuration.httpVersion = HttpVersion.HTTP_2_0
        def ssl = new ClientSslConfiguration()
        // the server certificate names server.example.com, not localhost
        ssl.sslContext = sslContext(clientCert, serverCert)
        ssl.endpointIdentification = endpointIdentification
        configuration.sslConfiguration = ssl
        return HttpClient.create(new URL("https://localhost:$server.port"), configuration)
    }

    private static SSLContext sslContext(SelfSignedCertificate key, SelfSignedCertificate trusted) {
        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        if (key != null) {
            ks.setKeyEntry("key", key.key(), "secret".toCharArray(), new Certificate[]{key.cert()})
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, "secret".toCharArray())

        KeyStore ts = KeyStore.getInstance("PKCS12")
        ts.load(null, null)
        ts.setCertificateEntry("cert", trusted.cert())
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ts)

        SSLContext context = SSLContext.getInstance("TLS")
        context.init(kmf.keyManagers, tmf.trustManagers, null)
        return context
    }

    @Controller
    @Requires(property = 'spec.name', value = 'ProvidedSslContextSpec')
    static class TestController {
        @Get('/provided-ssl')
        String get(HttpRequest<?> request) {
            def cert = request.getCertificate().get() as X509Certificate
            "$request.httpVersion $cert.subjectX500Principal.name"
        }
    }
}
