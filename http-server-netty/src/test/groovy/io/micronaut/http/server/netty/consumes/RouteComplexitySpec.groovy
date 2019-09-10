package io.micronaut.http.server.netty.consumes

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.AbstractMicronautSpec

import javax.annotation.Nullable

class RouteComplexitySpec extends AbstractMicronautSpec {

    void "test route complexity"() {
        when:
        String body = rxClient.retrieve(HttpRequest.GET("/test-complexity/somefile.xls")).blockingFirst()

        then:
        noExceptionThrown()
        body == "fallback"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/somefile.csv")).blockingFirst()

        then:
        noExceptionThrown()
        body == "csv"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/other/a/b/c/d")).blockingFirst()

        then:
        noExceptionThrown()
        body == "ab/c/d"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/list")).blockingFirst()

        then:
        noExceptionThrown()
        body == "list"
    }

    @Requires(property = "spec.name", value = "RouteComplexitySpec")
    @Controller("/test-complexity")
    static class MyController  {

        @Get("/{id}")
        HttpResponse oneVariable() {
            HttpResponse.ok("fallback")
        }

        @Get("/{id}.csv")
        HttpResponse twoRaw() {
            HttpResponse.ok("csv")
        }

        @Get("/other{/a}/{+b}")
        HttpResponse twoVariables(String a, String b) {
            HttpResponse.ok(a + b)
        }

        @Get("/other{/a}/{b}{/c}/d")
        HttpResponse threeVariablesTwoRaw() {
            HttpResponse.ok("two variables")
        }

        @Get("/list{?full}")
        String getFull(@Nullable Boolean full) {
            return "list"
        }
    }
}
