package io.micronaut.docs.web.router.routematch

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.web.router.RouteAttributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "RouteMatchSpec")
@MicronautTest
internal class RouteMatchTest {
    @Test
    fun testRouteMatchRetrieval(@Client("/") httpClient: HttpClient) {
        val client = httpClient.toBlocking()
        assertEquals("text/plain", client.retrieve(HttpRequest.GET<Any>("/routeMatch").accept(MediaType.TEXT_PLAIN), String::class.java))
    }

    @Requires(property = "spec.name", value = "RouteMatchSpec")
    @Controller
    internal class RouteMatchController {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/routeMatch")
//tag::routematch[]
        fun index(request: HttpRequest<*>): String? {
            val routeMatch = RouteAttributes.getRouteMatch(request)
                .orElse(null)
//end::routematch[]
            return routeMatch?.routeInfo?.produces?.stream()?.map { obj: MediaType -> obj.toString() }?.findFirst()?.orElse(null)
        }
    }
}
