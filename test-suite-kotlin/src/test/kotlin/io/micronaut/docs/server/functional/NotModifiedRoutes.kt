package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import io.micronaut.web.router.builder.RouteCondition
import io.micronaut.web.router.builder.ValueMatcher
import jakarta.inject.Singleton
// end::imports[]

private const val VERSION = "\"2026.10\""

@Requires(property = "spec.name", value = "NotModifiedRoutesTest")
// tag::clazz[]
@Singleton
class NotModifiedRoutes : HttpDirectRoutes {
    override fun routes(routes: DirectRouteBuilder) {
        routes.GET("/release")
            .where(RouteCondition.header(HttpHeaders.IF_NONE_MATCH, ValueMatcher.entityTag(VERSION))) // <1>
            .order(-1) // <2>
            .respond(HttpResponse.notModified<Any>().header(HttpHeaders.ETAG, VERSION))
        routes.GET("/release", HttpResponse.ok("release 2026.10") // <3>
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header(HttpHeaders.ETAG, VERSION))
    }
}
// end::clazz[]
