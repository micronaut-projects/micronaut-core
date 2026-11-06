package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * A filter {@code @Body} of a type the filter binder cannot fill leaves the filter unsatisfied,
 * and the body it buffered must still be released. The compile-time check of micronaut-http-validation
 * rejects such a filter; this spec is compiled without it.
 */
class FilterUnsupportedBodySpec extends Specification {
    def "unsupported filter body type releases the buffered body"() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ["spec.name": "FilterUnsupportedBodySpec"])
        def client = server.applicationContext.createBean(HttpClient, server.URI)
        def body = '{"a":"' + 'x' * (64 * 1024) + '"}'

        when:
        def statuses = (1..10).collect {
            try {
                client.toBlocking().exchange(HttpRequest.POST("/filter-unsupported-body", body).contentType(MediaType.APPLICATION_JSON_TYPE), String)
                return HttpStatus.OK
            } catch (HttpClientResponseException e) {
                return e.status
            }
        }

        then:
        statuses.every { it == HttpStatus.INTERNAL_SERVER_ERROR }

        cleanup:
        client.close()
        server.close()
    }

    @Requires(property = "spec.name", value = "FilterUnsupportedBodySpec")
    @ServerFilter("/filter-unsupported-body")
    static class MapBodyFilter {
        @RequestFilter
        void filter(@Body Map<String, Object> body) {
            // never called: the body cannot be bound to a map, so the request fails before the filter runs
        }
    }

    @Requires(property = "spec.name", value = "FilterUnsupportedBodySpec")
    @Controller("/filter-unsupported-body")
    static class TestController {
        @Post
        String post(@Body String body) {
            return body
        }
    }
}
