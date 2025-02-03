package io.micronaut.docs.web.router.routematch

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.web.router.RouteAttributes
import io.micronaut.web.router.RouteMatch
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "RouteMatchSpec")
@MicronautTest
class RouteMatchTest extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    void testRouteMatchRetrieval() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        expect:
        "text/plain" == client.retrieve(HttpRequest.GET("/routeMatch").accept(MediaType.TEXT_PLAIN), String)
    }

    @Requires(property = "spec.name", value = "RouteMatchSpec")
    @Controller
    static class RouteMatchController {

        @Produces(MediaType.TEXT_PLAIN)
        @Get("/routeMatch")
//tag::routematch[]
        String index(HttpRequest<?> request) {
            RouteMatch<?> routeMatch = RouteAttributes.getRouteMatch(request)
                    .orElse(null)
//end::routematch[]
            return routeMatch != null ? routeMatch.getRouteInfo().getProduces().stream().map(MediaType::toString).findFirst().orElse(null) : null
        }
    }
}
