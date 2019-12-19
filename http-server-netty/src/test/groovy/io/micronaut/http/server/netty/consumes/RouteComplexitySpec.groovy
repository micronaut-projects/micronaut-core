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
        String body = rxClient.retrieve(HttpRequest.GET("/test-complexity/id/somefile.xls")).blockingFirst()

        then:
        noExceptionThrown()
        body == "fallback"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/id/somefile.csv")).blockingFirst()

        then:
        noExceptionThrown()
        body == "csv"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/other/a/b/c/d")).blockingFirst()

        then:
        noExceptionThrown()
        body == "ab/c"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/other2/a/b/c")).blockingFirst()

        then:
        noExceptionThrown()
        body == "ab/c"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/list")).blockingFirst()

        then:
        noExceptionThrown()
        body == "list"

        when:
        body = rxClient.retrieve(HttpRequest.GET("/test-complexity/length/abc")).blockingFirst()

        then:
        noExceptionThrown()
        body == "abc"
    }

    @Requires(property = "spec.name", value = "RouteComplexitySpec")
    @Controller("/test-complexity")
    static class MyController  {

        @Get("/id/{id}")
        HttpResponse oneVariable() {
            HttpResponse.ok("fallback")
        }

        @Get("/id/{id}.csv")
        HttpResponse twoRaw() {
            HttpResponse.ok("csv")
        }

        @Get("/other{/a}/{+b}/d")
        HttpResponse twoVariables(String a, String b) {
            HttpResponse.ok(a + b)
        }

        @Get("/other{/a}/{b}{/c}/d")
        HttpResponse threeVariablesTwoRaw() {
            HttpResponse.ok("three variables")
        }

        @Get("/other2{/a}/{+b}")
        HttpResponse twoVariables2(String a, String b) {
            HttpResponse.ok(a + b)
        }

        @Get("/other2{/a}/{b}{/c}")
        HttpResponse threeVariablesOneRaw() {
            HttpResponse.ok("three variables one raw")
        }

        @Get("/list{?full}")
        String getFull(@Nullable Boolean full) {
            "list"
        }

        @Get("/{+path}")
        String pathPlus(String path) {
            path
        }

        @Get("/length/{level1}") // only 1 raw segment, but raw segment length has priority
        String lengthCompare(String level1) {
            level1
        }

        @Get("/{level1}/{level2}") // 2 raw segments
        String lengthCompare2(String level1, String level2) {
            level1
        }
    }
}
