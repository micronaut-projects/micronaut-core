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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.security.cert.X509Certificate

class SslRefreshSpec extends Specification {

    @Shared List<String> ciphers = ['TLS_RSA_WITH_AES_128_CBC_SHA',
                                    'TLS_RSA_WITH_AES_256_CBC_SHA',
                                    'TLS_RSA_WITH_AES_128_GCM_SHA256',
                                    'TLS_RSA_WITH_AES_256_GCM_SHA384',
                                    'TLS_DHE_RSA_WITH_AES_128_GCM_SHA256',
                                    'TLS_DHE_RSA_WITH_AES_256_GCM_SHA384',
                                    'TLS_DHE_DSS_WITH_AES_128_GCM_SHA256',
                                    'TLS_DHE_DSS_WITH_AES_256_GCM_SHA384']
    @Shared Map<String, Object> config = [
            'spec.name': 'SslRefreshSpec',
            'micronaut.ssl.enabled': true,
            'micronaut.server.ssl.client-authentication': 'NEED',
            'micronaut.server.ssl.key-store.path': 'classpath:certs/server.p12',
            'micronaut.server.ssl.key-store.password': 'secret',
            'micronaut.server.ssl.trust-store.path': 'classpath:certs/truststore',
            'micronaut.server.ssl.trust-store.password': 'secret',
            'micronaut.server.ssl.ciphers': ciphers,
            'micronaut.http.client.ssl.client-authentication': 'NEED',
            'micronaut.http.client.ssl.key-store.path': 'classpath:certs/client1.p12',
            'micronaut.http.client.ssl.key-store.password': 'secret'
    ]
    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext
                                                            .builder()
                                                            .propertySources(PropertySource.of(config))
                                                            .run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext
                                                        .createBean(HttpClient, embeddedServer.getURI())

    void "test ssl refresh"() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.GET('/ssl/refresh'), Map)

        then:
        response.status() == HttpStatus.OK
        response.body().ciphers == ciphers
        response.body().subjectDN == 'CN=test.example.com, OU=IT, O=Whatever, L=Munich, ST=Bavaria, C=DE, EMAILADDRESS=info@example.com'

        when:
        config.putAll(  'micronaut.server.ssl.key-store.path': 'classpath:keystore.p12',
                        'micronaut.server.ssl.key-store.password': 'foobar',
                        'micronaut.server.ssl.key-store.type': 'PKCS12',
                        'micronaut.server.ssl.ciphers': ciphers[0..4])
        def diff = embeddedServer.applicationContext.environment.refreshAndDiff()
        embeddedServer.applicationContext
                .getBean(Argument.of(ApplicationEventPublisher, RefreshEvent))
                .publishEvent(new RefreshEvent(diff))
        response = client.toBlocking().exchange(HttpRequest.GET('/ssl/refresh'), Map)

        then:
        response.status() == HttpStatus.OK
        response.body().ciphers == ciphers[0..4]
        response.body().subjectDN == 'CN=example.local, OU=IT Department, O=Global Security, L=London, ST=London, C=GB'
    }

    @Controller("/ssl/refresh")
    @Requires(property = 'spec.name',value = 'SslRefreshSpec')
    static class TestSslRefresh {
        @Get
        HttpResponse<Map<String, Object>> test(HttpRequest request) {
            def pipeline = (request as NettyHttpRequest).getChannelHandlerContext().pipeline()
            def sslHandler = pipeline.get(SslHandler)

            def engine = sslHandler.engine()
            X509Certificate cert = engine.getSession().getLocalCertificates()[0]
            return HttpResponse.ok([
                    ciphers: engine.enabledCipherSuites,
                    subjectDN: cert.subjectDN.toString()
            ] as Map<String, Object>)
        }
    }
}
