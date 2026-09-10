package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class FilterJsonArrayResponseSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'FilterJsonArrayResponseSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URI)

    void "reactive response body without a route is formatted as a json array"() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/filter-json/objects"), String)

        then:
        response.header("Content-Type").startsWith(MediaType.APPLICATION_JSON)
        response.body() == '[{"n":1},{"n":2}]'
    }

    void "reactive byte[] response body without a route is written unchanged"() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/filter-json/raw"), String)

        then:
        response.body() == '{"n":1}{"n":2}'
    }

    void "reactive response body without a route and without a json content type is written unchanged"() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/filter-json/text"), String)

        then:
        response.body() == 'foobar'
    }

    @Singleton
    @Requires(property = "spec.name", value = "FilterJsonArrayResponseSpec")
    @ServerFilter("/filter-json/**")
    static class ShortCircuitFilter {
        @RequestFilter
        HttpResponse<?> request(HttpRequest<?> request) {
            switch (request.path) {
                case "/filter-json/objects":
                    return HttpResponse.ok(Flux.just([n: 1], [n: 2]))
                            .contentType(MediaType.APPLICATION_JSON_TYPE)
                case "/filter-json/raw":
                    return HttpResponse.ok(Flux.just(
                            '{"n":1}'.getBytes(StandardCharsets.UTF_8),
                            '{"n":2}'.getBytes(StandardCharsets.UTF_8)))
                            .contentType(MediaType.APPLICATION_JSON_TYPE)
                default:
                    return HttpResponse.ok(Flux.just("foo", "bar"))
                            .contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }
    }

    @Controller("/filter-json")
    @Requires(property = "spec.name", value = "FilterJsonArrayResponseSpec")
    static class NeverCalledController {
        @Get("/{path}")
        String index() {
            throw new AssertionError("Filter should have short-circuited the request")
        }
    }
}
