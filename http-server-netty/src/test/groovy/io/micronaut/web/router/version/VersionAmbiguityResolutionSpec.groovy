package io.micronaut.web.router.version

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class VersionAmbiguityResolutionSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                 : 'VersionAmbiguityResolutionSpec',
            "micronaut.router.versioning.enabled"       : "true",
            "micronaut.router.versioning.header.enabled": "true"
    ])

    @AutoCleanup
    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "a less specific route of the requested version is not dropped in favour of a more specific route of another version"() {
        expect:
        client.retrieve(HttpRequest.GET(path).header("X-API-VERSION", version)) == expected

        where:
        path             | version | expected
        "/items/special" | "1"     | "v1:special"
        "/items/special" | "2"     | "v2:special"
        "/items/other"   | "1"     | "v1:other"
    }

    void "a route of the requested version is not dropped in favour of a route of another version that produces the preferred accepted type"() {
        expect:
        client.retrieve(HttpRequest.GET("/produces").accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_PLAIN_TYPE).header("X-API-VERSION", "1")) == "v1"
    }

    void "the router applies the route filter to closest matches and to find by request and uri"() {
        given:
        Router router = embeddedServer.applicationContext.getBean(Router)
        HttpRequest<?> request = HttpRequest.GET("/items/special").header("X-API-VERSION", "1")

        when:
        UriRouteMatch closest = router.findClosest(request)
        List<UriRouteMatch> allClosest = router.findAllClosest(request)
        List<UriRouteMatch> found = router.find(request, "/items/special").toList()

        then:
        closest.executableMethod.methodName == "itemV1"
        allClosest*.executableMethod*.methodName == ["itemV1"]
        found*.executableMethod*.methodName == ["itemV1"]
    }

    @Requires(property = "spec.name", value = "VersionAmbiguityResolutionSpec")
    @Controller
    static class ItemsController {

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1")
        @Get("/items/{id}")
        String itemV1(String id) {
            "v1:" + id
        }

        @Produces(MediaType.TEXT_PLAIN)
        @Version("2")
        @Get("/items/special")
        String specialV2() {
            "v2:special"
        }

        @Produces(MediaType.TEXT_PLAIN)
        @Version("1")
        @Get("/produces")
        String producesV1() {
            "v1"
        }

        @Produces(MediaType.APPLICATION_JSON)
        @Version("2")
        @Get("/produces")
        String producesV2() {
            '"v2"'
        }
    }
}
