package io.micronaut.docs.web.router.routematch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.RouteAttributes
import io.micronaut.web.router.RouteMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RouteMatchTest {

    @Test
    fun testRouteMatchRetrieval() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "RouteMatchSpec")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { httpClient ->
                val request = HttpRequest.GET<Any>("/routeMatch").accept(MediaType.TEXT_PLAIN)
                assertEquals("text/plain", httpClient.toBlocking().retrieve(request, String::class.java))
            }
        }
    }
}

@Requires(property = "spec.name", value = "RouteMatchSpec")
@Controller
internal class RouteMatchController {

    @Produces(MediaType.TEXT_PLAIN)
    @Get("/routeMatch")
//tag::routematch[]
    fun index(request: HttpRequest<*>): String? {
        val routeMatch: RouteMatch<*>? = RouteAttributes.getRouteMatch(request)
            .orElse(null)
//end::routematch[]
        return routeMatch?.routeInfo?.produces?.firstOrNull()?.toString()
    }
}
