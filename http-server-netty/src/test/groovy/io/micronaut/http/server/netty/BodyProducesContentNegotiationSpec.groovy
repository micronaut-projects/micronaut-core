package io.micronaut.http.server.netty

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.hateoas.AbstractResource
import io.micronaut.http.hateoas.VndError
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * A body type annotated with {@link io.micronaut.http.annotation.Produces} only supplies the default
 * content type: the Accept header selects among the types the route declares.
 */
@MicronautTest
@Property(name = "spec.name", value = "BodyProducesContentNegotiationSpec")
class BodyProducesContentNegotiationSpec extends Specification {

    static final String HAL = MediaType.APPLICATION_HAL_JSON
    static final String JSON = MediaType.APPLICATION_JSON
    static final String VND_ERROR = MediaType.APPLICATION_VND_ERROR

    @Inject
    EmbeddedServer server

    @Unroll
    void "GET #path with Accept #accept responds with #expected"() {
        when:
        def response = get(path, accept)

        then:
        response.statusCode() == 200
        response.headers().firstValue("Content-Type").orElse(null) == expected
        response.body().contains('"name":"Title"')

        where:
        path                           | accept          | expected
        // the route declares both types: the Accept header decides
        '/body-produces/both'          | JSON            | JSON
        '/body-produces/both'          | HAL             | HAL
        '/body-produces/both'          | "$JSON, $HAL"   | JSON
        '/body-produces/both-mono'     | JSON            | JSON
        '/body-produces/both-mono'     | HAL             | HAL
        '/body-produces/both-future'   | JSON            | JSON
        '/body-produces/both-future'   | HAL             | HAL
        // Accept selects no declared type: the body type stays the default
        '/body-produces/both'          | null            | HAL
        '/body-produces/both'          | '*/*'           | HAL
        '/body-produces/json-first'    | null            | HAL
        '/body-produces/json-first'    | JSON            | JSON
        // the route declares no produces: the body type is the content type
        '/body-produces/undeclared'    | null            | HAL
        '/body-produces/undeclared'    | JSON            | HAL
        // a content type set by the controller always wins
        '/body-produces/explicit'      | JSON            | HAL
        '/body-produces/explicit-json' | HAL             | JSON
    }

    @Unroll
    void "an error body keeps its type on a route that does not declare it, Accept #accept"() {
        when:
        def response = get('/body-produces/vnd-error', accept)

        then:
        response.headers().firstValue("Content-Type").orElse(null) == VND_ERROR

        where:
        accept << [null, JSON]
    }

    void "a route that completes without a body still responds 404 when the Accept header selects a declared type"() {
        expect:
        get('/body-produces/both-future-empty', JSON).statusCode() == 404
    }

    private java.net.http.HttpResponse<String> get(String path, String accept) {
        def builder = java.net.http.HttpRequest.newBuilder(server.URI.resolve(path)).GET()
        if (accept != null) {
            builder.header("Accept", accept)
        }
        try (def client = java.net.http.HttpClient.newHttpClient()) {
            return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
        }
    }

    @Requires(property = "spec.name", value = "BodyProducesContentNegotiationSpec")
    @Controller(value = "/body-produces", produces = [MediaType.APPLICATION_HAL_JSON, MediaType.APPLICATION_JSON])
    static class BothController {

        @Get("/both")
        BookResource both() {
            new BookResource("Title")
        }

        @Get("/both-mono")
        Mono<BookResource> bothMono() {
            Mono.just(new BookResource("Title"))
        }

        @Get("/both-future")
        CompletionStage<BookResource> bothFuture() {
            CompletableFuture.completedFuture(new BookResource("Title"))
        }

        @Get("/both-future-empty")
        CompletionStage<BookResource> bothFutureEmpty() {
            CompletableFuture.completedFuture(null)
        }

        @Get(value = "/json-first", produces = [MediaType.APPLICATION_JSON, MediaType.APPLICATION_HAL_JSON])
        BookResource jsonFirst() {
            new BookResource("Title")
        }

        @Get("/explicit")
        HttpResponse<BookResource> explicit() {
            HttpResponse.ok(new BookResource("Title")).contentType(MediaType.APPLICATION_HAL_JSON_TYPE)
        }

        @Get("/explicit-json")
        HttpResponse<BookResource> explicitJson() {
            HttpResponse.ok(new BookResource("Title")).contentType(MediaType.APPLICATION_JSON_TYPE)
        }
    }

    @Requires(property = "spec.name", value = "BodyProducesContentNegotiationSpec")
    @Controller("/body-produces")
    static class UndeclaredController {

        @Get("/undeclared")
        BookResource undeclared() {
            new BookResource("Title")
        }

        @Get("/vnd-error")
        VndError vndError() {
            new VndError("Oops")
        }
    }

    static class BookResource extends AbstractResource<BookResource> {
        final String name

        BookResource(String name) {
            this.name = name
        }
    }
}
