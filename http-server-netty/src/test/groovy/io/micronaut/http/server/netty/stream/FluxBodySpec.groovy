package io.micronaut.http.server.netty.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
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

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FluxBodySpec'])
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

    void "bind a declared Flux on the streaming path for #mediaType"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST('/body/flux/test/json', body)
            .contentType(mediaType)) == '[{"name":"a"},{"name":"b"}]'

        where:
        mediaType                              | body
        MediaType.APPLICATION_JSON_TYPE        | '[{"name":"a"},{"name":"b"}]'
        MediaType.APPLICATION_JSON_STREAM_TYPE | '{"name":"a"}\n{"name":"b"}' 
    }

    @Requires(property = 'spec.name', value = 'FluxBodySpec')
    @Controller("/body/flux/test")
    static class ReactiveController {

        @Post(value = '/json', consumes = [MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM])
        Mono<List<Map<String, Object>>> json(@Body Flux<Map<String, Object>> body) {
            body.collectList()
        }

        @Post("/")
        Mono<HttpResponse<?>> read(@Body Publisher<String> body) {
            return Flux.from(body).collectList()
                    .map(list -> HttpResponse.ok(list))
        }
    }
}
