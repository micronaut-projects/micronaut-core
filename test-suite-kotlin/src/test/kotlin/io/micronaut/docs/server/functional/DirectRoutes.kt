package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import io.micronaut.web.router.builder.RouteCondition
import io.micronaut.web.router.builder.ValueMatcher
import jakarta.inject.Singleton
import java.util.EnumSet
// end::imports[]

@Requires(property = "spec.name", value = "DirectRoutesTest")
// tag::clazz[]
@Singleton
class DirectRoutes : HttpDirectRoutes {
    companion object {
        private const val VERSION = "\"1.0.0\""
    }

    override fun routes(routes: DirectRouteBuilder) {
        routes.path("/probe") { probe -> // <1>
            probe.GET("/live", HttpResponse.ok("UP")) // <2>
            probe.GET("/{component}") // <3>
                .constrain("component", listOf("db", "cache"))
                .respond { direct -> HttpResponse.ok(direct.pathVariables().getString("component") + " UP") }
        }
        routes.GET("/robots.txt", HttpResponse.ok("User-agent: *\nDisallow: /private/\n")
            .contentType(MediaType.TEXT_PLAIN_TYPE)) // <4>
        routes.GET("/version").respond { direct ->
            if (VERSION == direct.request().header(HttpHeaders.IF_NONE_MATCH)) HttpResponse.notModified<Any>() // <5>
            else HttpResponse.ok("1.0.0").header(HttpHeaders.ETAG, VERSION)
        }
        routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE), "/{+path}") // <6>
            .where(RouteCondition.any(
                RouteCondition.header("User-Agent", ValueMatcher.contains("badbot").ignoringCase()),
                RouteCondition.peerAddress("203.0.113.0/24")))
                .respond(HttpResponse.status<Any>(HttpStatus.FORBIDDEN)) // <7>
    }
}
// end::clazz[]
