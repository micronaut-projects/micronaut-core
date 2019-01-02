package io.micronaut.http.server.netty.consumes

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.netty.AbstractMicronautSpec

import static io.micronaut.http.MediaType.*

class ConsumesMatchesRouteSpec extends AbstractMicronautSpec {

    void "test routes are filtered by consumes"() {
        when:
        String body = rxClient.retrieve(HttpRequest.POST("/test-consumes", [x: 1]).contentType(APPLICATION_JSON_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "json"

        when:
        body = rxClient.retrieve(HttpRequest.POST("/test-consumes", "abc").contentType(APPLICATION_GRAPHQL_TYPE)).blockingFirst()

        then:
        noExceptionThrown()
        body == "graphql"
    }

    @Requires(property = "spec.name", value = "ConsumesMatchesRouteSpec")
    @Controller("/test-consumes")
    static class MyController  {

        @Post(consumes = APPLICATION_JSON)
        HttpResponse posta(@Body String body) {
            HttpResponse.ok("json")
        }

        @Post(consumes = APPLICATION_GRAPHQL)
        HttpResponse postb(@Body String body) {
            HttpResponse.ok("graphql")
        }
    }
}
