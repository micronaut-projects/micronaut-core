package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class FluxBodySpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURI())

    void "test empty and non-empty flux"() {
        when:
        def response = client.toBlocking()
                .exchange(HttpRequest.POST("/body/flux/test", "Some content"), String)

        then:
        response.status() == HttpStatus.OK
        response.body() == '["Some content"]'

        when:
        response = client.toBlocking()
                .exchange(HttpRequest.POST("/body/flux/test", null), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status() == HttpStatus.BAD_REQUEST
    }

    @Controller("/body/flux/test")
    static class ReactiveController {

        @Post("/")
        Mono<HttpResponse<?>> read(@Body Publisher<String> body) {
            return Flux.from(body).collectList()
                    .map(list -> HttpResponse.ok(list))
        }
    }
}
