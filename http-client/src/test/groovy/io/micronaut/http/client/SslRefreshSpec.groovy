package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.SelfSignedCertificate
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate

@IgnoreIf({ os.isMacOs() })
class SslRefreshSpec extends Specification {

    @Shared List<String> ciphers = ['TLS_RSA_WITH_AES_128_CBC_SHA',
                                    'TLS_RSA_WITH_AES_256_CBC_SHA',
                                    'TLS_RSA_WITH_AES_128_GCM_SHA256',
                                    'TLS_RSA_WITH_AES_256_GCM_SHA384',
                                    'TLS_DHE_RSA_WITH_AES_128_GCM_SHA256',
                                    'TLS_DHE_RSA_WITH_AES_256_GCM_SHA384',
                                    'TLS_DHE_DSS_WITH_AES_128_GCM_SHA256',
                                    'TLS_DHE_DSS_WITH_AES_256_GCM_SHA384']
    @Shared Path keyStorePath
    @Shared Path trustStorePath
    @Shared Map<String, Object> config = [
            'spec.name': 'SslRefreshSpec',
            'micronaut.ssl.enabled': true,
            'micronaut.server.ssl.port':-1,
            'micronaut.server.ssl.client-authentication': 'NEED',
            'micronaut.server.ssl.key-store.path': 'classpath:certs/server.p12',
            'micronaut.server.ssl.key-store.password': 'secret',
            'micronaut.server.ssl.ciphers': ciphers,
            'micronaut.http.client.ssl.client-authentication': 'NEED',
            'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
            'micronaut.http.client.pool.enabled': false,
            // need to force http1 because our ciphers are not supported by http2
            'micronaut.http.client.http-version': '1.1',
    ]

    private void makeClientCert(SelfSignedCertificate certificate) {
        keyStorePath = Files.createTempFile("micronaut-test-key-store", "pkcs12")
        trustStorePath = Files.createTempFile("micronaut-test-trust-store", "pkcs12")

        KeyStore ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("key", certificate.key(), "".toCharArray(), new Certificate[]{certificate.cert()})
        try (OutputStream os = Files.newOutputStream(keyStorePath)) {
            ks.store(os, "".toCharArray())
        }

        KeyStore ts = KeyStore.getInstance("JKS")
        ts.load(null, null)
        ts.setCertificateEntry("cert", certificate.cert())
        try (OutputStream os = Files.newOutputStream(trustStorePath)) {
            ts.store(os, "123456".toCharArray())
        }

        config.putAll([
                'micronaut.server.ssl.trust-store.path': 'file://' + trustStorePath.toString(),
                'micronaut.server.ssl.trust-store.type': 'JKS',
                'micronaut.server.ssl.trust-store.password': '123456',
                'micronaut.http.client.ssl.key-store.path': 'file://' + keyStorePath.toString(),
                'micronaut.http.client.ssl.key-store.type': 'PKCS12',
                'micronaut.http.client.ssl.key-store.password': '',
        ])
    }

    void "test server ssl refresh"() {
        given:
        makeClientCert(new SelfSignedCertificate("client1"))
        config.put('micronaut.server.ssl.ciphers', ciphers)

        EmbeddedServer embeddedServer = ApplicationContext
                .builder()
                .propertySources(PropertySource.of(config))
                .run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext
                .createBean(HttpClient, embeddedServer.getURI())

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET('/ssl/refresh/server'), Map)

        then:
        response.status() == HttpStatus.OK
        response.body().ciphers == ciphers
        response.body().subjectDN == 'CN=test.example.com, OU=IT, O=Whatever, L=Munich, ST=Bavaria, C=DE, EMAILADDRESS=info@example.com'

        when:
        config.putAll('micronaut.server.ssl.key-store.path': 'classpath:keystore.p12',
                        'micronaut.server.ssl.key-store.password': 'foobar',
                        'micronaut.server.ssl.key-store.type': 'PKCS12',
                        'micronaut.server.ssl.ciphers': ciphers[0..4])
        def diff = embeddedServer.applicationContext.environment.refreshAndDiff()
        embeddedServer.applicationContext
                .getBean(Argument.of(ApplicationEventPublisher, RefreshEvent))
                .publishEvent(new RefreshEvent(diff))
        response = client.toBlocking().exchange(HttpRequest.GET('/ssl/refresh/server'), Map)

        then:
        response.status() == HttpStatus.OK
        response.body().ciphers == ciphers[0..4]
        response.body().subjectDN == 'CN=example.local, OU=IT Department, O=Global Security, L=London, ST=London, C=GB'

        cleanup:
        embeddedServer.close()
        client.close()
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
    }

    void "test client ssl refresh"() {
        given:
        makeClientCert(new SelfSignedCertificate("client1"))

        EmbeddedServer embeddedServer = ApplicationContext
                .builder()
                .propertySources(PropertySource.of(config))
                .run(EmbeddedServer)
        HttpClient client = embeddedServer.applicationContext
                .createBean(HttpClient, embeddedServer.getURI())

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET('/ssl/refresh/client'), Map)

        then:
        response.status() == HttpStatus.OK
        response.body().subjectDN == 'CN=client1'

        when:
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
        makeClientCert(new SelfSignedCertificate("client2"))

        def diff = embeddedServer.applicationContext.environment.refreshAndDiff()
        embeddedServer.applicationContext
                .getBean(Argument.of(ApplicationEventPublisher, RefreshEvent))
                .publishEvent(new RefreshEvent(diff))
        response = client.toBlocking().exchange(HttpRequest.GET('/ssl/refresh/client'), Map)

        then:
        response.status() == HttpStatus.OK
        response.body().subjectDN == 'CN=client2'

        cleanup:
        embeddedServer.close()
        client.close()
        Files.deleteIfExists(keyStorePath)
        Files.deleteIfExists(trustStorePath)
    }

    @Controller("/ssl/refresh")
    @Requires(property = 'spec.name', value = 'SslRefreshSpec')
    static class TestSslRefresh {
        @Get("/server")
        HttpResponse<Map<String, Object>> testServer(HttpRequest request) {
            def pipeline = (request as NettyHttpRequest).getChannelHandlerContext().pipeline()
            def sslHandler = pipeline.get(SslHandler)

            def engine = sslHandler.engine()
            X509Certificate cert = engine.getSession().getLocalCertificates()[0]
            return HttpResponse.ok([
                    ciphers  : engine.enabledCipherSuites,
                    subjectDN: cert.subjectDN.toString()
            ] as Map<String, Object>)
        }

        @Get("/client")
        HttpResponse<Map<String, Object>> testClient(HttpRequest request) {
            def pipeline = (request as NettyHttpRequest).getChannelHandlerContext().pipeline()
            def sslHandler = pipeline.get(SslHandler)

            def engine = sslHandler.engine()
            X509Certificate cert = engine.getSession().getPeerCertificates()[0]
            return HttpResponse.ok([
                    subjectDN: cert.subjectDN.toString()
            ] as Map<String, Object>)
        }
    }
}
