package io.micronaut.http.server.netty.jackson

import com.fasterxml.jackson.annotation.JsonView
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class JsonViewWrappedBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'jackson.json-view.enabled': true,
            'spec.name'                : 'JsonViewWrappedBodySpec'
    ])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    def "@JsonView on a #path @Body excludes properties outside the view"() {
        when:
        String password = client.toBlocking().retrieve(HttpRequest.POST("/jsonview-wrapped/body/$path",
                '{"firstName":"mallory","password":"injected"}').contentType(MediaType.APPLICATION_JSON), String)

        then:
        password == 'null'

        where:
        // Mono and Flux are bound through the Publisher binder (converted by micronaut-reactor, not on this classpath)
        path << ['plain', 'publisher', 'future']
    }

    def "@JsonView applies when Accept selects the second produced type #accept"() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/jsonview-wrapped/$path").header(HttpHeaders.ACCEPT, accept), String)

        then:
        body.contains('Bob')
        !body.contains('secret')
        !body.contains('08/01/1980')

        where:
        path          | accept
        'two-types'   | MediaType.APPLICATION_JSON
        'two-types'   | MediaType.APPLICATION_JSON_STREAM
        'sse-or-json' | MediaType.TEXT_EVENT_STREAM
    }

    def "@JsonView applies to SSE #path"() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/jsonview-wrapped/$path").header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM), String)

        then:
        body.contains('Bob')
        !body.contains('secret')

        where:
        path << ['sse-values', 'sse-events']
    }

    @Controller("/jsonview-wrapped")
    @Requires(property = "spec.name", value = "JsonViewWrappedBodySpec")
    static class WrappedController {

        static TestModel model() {
            new TestModel(firstName: "Bob", lastName: "Jones", birthdate: "08/01/1980", password: "secret")
        }

        @Post(value = "/body/plain", produces = MediaType.TEXT_PLAIN)
        String plain(@Body @JsonView(Views.Public) TestModel m) {
            String.valueOf(m.password)
        }

        @Post(value = "/body/mono", produces = MediaType.TEXT_PLAIN)
        Mono<String> mono(@Body @JsonView(Views.Public) Mono<TestModel> m) {
            m.map { String.valueOf(it.password) }
        }

        @Post(value = "/body/flux", produces = MediaType.TEXT_PLAIN)
        Mono<String> flux(@Body @JsonView(Views.Public) Flux<TestModel> m) {
            m.next().map { String.valueOf(it.password) }
        }

        @Post(value = "/body/publisher", produces = MediaType.TEXT_PLAIN)
        Mono<String> publisher(@Body @JsonView(Views.Public) Publisher<TestModel> m) {
            Mono.from(m).map { String.valueOf(it.password) }
        }

        @Post(value = "/body/future", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> future(@Body @JsonView(Views.Public) CompletableFuture<TestModel> m) {
            m.thenApply { String.valueOf(it.password) }
        }

        @JsonView(Views.Public)
        @Get(value = "/two-types", produces = [MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM])
        Flux<TestModel> twoTypes() {
            Flux.just(model())
        }

        @JsonView(Views.Public)
        @Get(value = "/sse-or-json", produces = [MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM])
        Flux<TestModel> sseOrJson() {
            Flux.just(model())
        }

        @JsonView(Views.Public)
        @Get(value = "/sse-values", produces = MediaType.TEXT_EVENT_STREAM)
        Flux<TestModel> sseValues() {
            Flux.just(model())
        }

        @JsonView(Views.Public)
        @Get(value = "/sse-events", produces = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<TestModel>> sseEvents() {
            Flux.just(Event.of(model()).name("created"))
        }
    }
}
