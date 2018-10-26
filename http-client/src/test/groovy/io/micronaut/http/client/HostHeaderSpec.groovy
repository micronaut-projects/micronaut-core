package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification

class HostHeaderSpec extends Specification {

    @IgnoreIf({ System.getenv("TRAVIS")})
    // Travis doesn't allow 80
    void "test host header with server on 80"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.build(['micronaut.server.port': 80]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost"

        cleanup:
        embeddedServer.close()
    }

    void "test host header with server on random port"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost:${embeddedServer.getURI().getPort()}"

        cleanup:
        embeddedServer.close()
    }

    @IgnoreIf({ System.getenv("TRAVIS")})
    // Travis doesn't allow 80
    void "test host header with client authority"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.build(['micronaut.server.port': 80]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(new URL("http://foo@localhost"))
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost"

        cleanup:
        embeddedServer.close()
    }

    @IgnoreIf({ System.getenv("TRAVIS")})
    // Travis doesn't allow 443
    void "test host header with https server on 443"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.build([
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': 443
        ]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost"

        cleanup:
        embeddedServer.close()
    }

    void "test host header with https server on custom port"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.build([
                'micronaut.ssl.enabled': true,
                'micronaut.ssl.buildSelfSigned': true
        ]).run(EmbeddedServer)
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/echo-host"),
                String
        )

        then:
        response.body() == "localhost:${embeddedServer.getURI().getPort()}"

        cleanup:
        embeddedServer.close()
    }

    @Controller("/echo-host")
    static class EchoHostController {

        @Get(produces = MediaType.TEXT_PLAIN)
        String simple(@Header String host) {
            host
        }
    }
}
