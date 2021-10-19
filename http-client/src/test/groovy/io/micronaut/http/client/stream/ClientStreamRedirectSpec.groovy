package io.micronaut.http.client.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientStreamRedirectSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            "spec.name": "ClientStreamRedirectSpec"
    ])
    @Shared HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    void "test a stream redirecting"() {
        when:
        String response = client.toBlocking().retrieve("/proxy")

        then:
        response == "data"
    }

    @Requires(property = "spec.name", value = "ClientStreamRedirectSpec")
    @Controller
    static class Temp {

        @Inject @Client("/")
        StreamingHttpClient httpClient

        @Get(uri = "/proxy", produces = MediaType.APPLICATION_OCTET_STREAM)
        Flux<byte[]> proxy() {
            Flux.from(httpClient.dataStream(HttpRequest.GET("/redirect")))
                    .doOnComplete(() -> System.out.println("completed"))
                    .map(ByteBuffer::toByteArray)
        }

        @Get("/redirect")
        HttpResponse redirect() {
            HttpResponse.redirect(URI.create("/data"))
        }

        @Get("/data")
        String someData() {
            "data"
        }
    }
}
