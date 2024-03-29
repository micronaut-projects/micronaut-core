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

class SslSelfSignedSpec extends Specification {

    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    EmbeddedServer embeddedServer
    HttpClient client

    void setup() {
        embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'SslSelfSignedSpec',
                'micronaut.ssl.enabled': true,
                'micronaut.server.ssl.buildSelfSigned': true,
                'micronaut.server.ssl.port': -1,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ])
        client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
    }

    void cleanup() {
        client.close()
        embeddedServer.close()
    }

    void "expect the url to be https"() {
        expect:
        embeddedServer.getURL().toString().startsWith("https://${host}:")
    }

    void "test send https request"() {
        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.GET("/ssl"), String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.body() == "Hello"
    }

    @Requires(property = 'spec.name', value = 'SslSelfSignedSpec')
    @Controller('/')
    static class SslSelfSignedController {

        @Get('/ssl')
        String simple() {
            return "Hello"
        }
    }
}
