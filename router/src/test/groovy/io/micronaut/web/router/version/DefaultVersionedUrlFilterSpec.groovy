package io.micronaut.web.router.version

import io.micronaut.context.DefaultApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.web.router.DefaultRouteBuilder
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.version.strategy.HeaderVersionExtractingStrategy
import io.micronaut.web.router.version.strategy.ParameterVersionExtractingStrategy
import spock.lang.Specification

import java.util.stream.Collectors

class DefaultVersionedUrlFilterSpec extends Specification {

    List<UriRouteMatch<Object, Object>> routes;

    def strategies = [new HeaderVersionExtractingStrategy(
            new RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration().with {
                name = "API-VERSION"
                it
            })]

    def setup() {
        def context = new DefaultApplicationContext("test").start();
        def controller = new VersionedController()
        context.registerSingleton(controller)

        def router = new DefaultRouter(new DefaultRouteBuilder(context) {
            {
                GET("/versioned/hello", controller, "helloV1")
                GET("/versioned/hello", controller, "helloV2")
                GET("/versioned/hello", controller, "duplicatedHelloV2")
                GET("/versioned/hello", controller, "hello")
            }
        })
        routes = router.find(HttpMethod.GET, "/versioned/hello").collect(Collectors.toList())
    }

    def "should extract header version from request"() {
        when:
        def strategy = new HeaderVersionExtractingStrategy(
                new RoutesVersioningConfiguration.HeaderBasedVersioningConfiguration().with {
                    name = specifiedHeader
                    it
                }
        )
        def request = HttpRequest.GET("/test").header(exactHeader, "1")
        then:
        result == strategy.extract(request)
        where:
        specifiedHeader | exactHeader   || result
        "API-VERSION"   | "API-VERSION" || Optional.of("1")
        "API-VERSION"   | "VERSION"     || Optional.empty()
    }

    def "should extract parameter version from request"() {
        when:
        def strategy = new ParameterVersionExtractingStrategy(
                new RoutesVersioningConfiguration.ParameterBasedVersioningConfiguration().with {
                    name = specifiedParameter
                    it
                }
        )
        def request = HttpRequest.GET("/test").with {
            parameters.add(exactParameter, "1")
            it
        }
        then:
        result == strategy.extract(request)
        where:
        specifiedParameter | exactParameter || result
        "version"          | "version"      || Optional.of("1")
        "version"          | "vrsn"         || Optional.empty()
    }

    def "should return initial routes ignoring version"() {
        when:
        def strategies = []
        def handler = new VersioningRouteMatchesFilter(strategies)
        def request = HttpRequest.GET("/versioned/hello")
        then:
        routes == handler.filter(routes, request)
    }

    def "should return initial versions due to header provided"() {
        when:
        def handler = new VersioningRouteMatchesFilter(strategies)
        def request = HttpRequest.GET("/versioned/hello")
        then:
        routes == handler.filter(routes, request)
    }

    def "should return exact route for header version"() {
        when:
        def handler = new VersioningRouteMatchesFilter(strategies)
        def request = HttpRequest.GET("/versioned/hello").header("API-VERSION", "1")
        def matches = handler.filter(routes, request)
        then:
        matches.size() == 1
        matches.get(0).getExecutableMethod().methodName == "helloV1"
    }

    def "should return duplicating routes for header version"() {
        when:
        def handler = new VersioningRouteMatchesFilter(strategies)
        def request = HttpRequest.GET("/versioned/hello").header("API-VERSION", "2")
        def matches = handler.filter(routes, request)
        then:
        matches.size() == 2
    }

    @Controller("/versioned")
    static class VersionedController {

        @Version("1")
        @Get("/hello")
        String helloV1() {
            return "helloV1"
        }

        @Version("2")
        @Get("/hello")
        String helloV2() {
            return "helloV2";
        }

        @Version("2")
        @Get("/hello")
        String duplicatedHelloV2() {
            return "duplicatedHelloV2";
        }

        @Get("/hello")
        String hello() {
            return "hello"
        }

    }

}
