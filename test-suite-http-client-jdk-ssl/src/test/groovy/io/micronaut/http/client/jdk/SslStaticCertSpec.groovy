package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Shared
import spock.lang.Specification

class SslStaticCertSpec extends Specification {

    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    EmbeddedServer embeddedServer
    HttpClient client

    void setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'SslStaticCertSpec',
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.keyStore.path': 'classpath:keystore.p12',
                'micronaut.ssl.keyStore.password': 'foobar',
                'micronaut.ssl.keyStore.type': 'PKCS12',
                'micronaut.ssl.protocols': ['TLSv1.2'],
                'micronaut.server.ssl.port': -1,
                'micronaut.ssl.ciphers': ['TLS_RSA_WITH_AES_128_CBC_SHA',
                                          'TLS_RSA_WITH_AES_256_CBC_SHA',
                                          'TLS_RSA_WITH_AES_128_GCM_SHA256',
                                          'TLS_RSA_WITH_AES_256_GCM_SHA384',
                                          'TLS_DHE_RSA_WITH_AES_128_GCM_SHA256',
                                          'TLS_DHE_RSA_WITH_AES_256_GCM_SHA384',
                                          'TLS_DHE_DSS_WITH_AES_128_GCM_SHA256',
                                          'TLS_DHE_DSS_WITH_AES_256_GCM_SHA384'],
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true
        ])
        client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
    }

    void cleanup() {
        client.close()
        embeddedServer.close()
    }

    void "expect the url to be https"() {
        expect:
        embeddedServer.getURL().toString() == "https://${host}:${embeddedServer.port}"
    }

    void "test send https request"() {
        when:
        Flux<HttpResponse<String>> reactiveSequence = Flux.from(client.exchange(
                HttpRequest.GET("/ssl/static"), String
        ))
        HttpResponse<String> response = reactiveSequence.blockFirst()

        then:
        response.body() == "Hello"
    }

    @Requires(property = 'spec.name', value = 'SslStaticCertSpec')
    @Controller('/')
    static class SslStaticController {

        @Get('/ssl/static')
        String simple() {
            return "Hello"
        }

    }
}
