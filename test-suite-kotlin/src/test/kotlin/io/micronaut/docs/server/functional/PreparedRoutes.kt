package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import jakarta.inject.Singleton
// end::imports[]

private const val VERSION = "\"2026.10\""

@Requires(property = "spec.name", value = "PreparedRoutesTest")
// tag::clazz[]
@Singleton
class PreparedRoutes : HttpDirectRoutes {
    override fun routes(routes: DirectRouteBuilder) {
        val current = routes.prepare(HttpResponse.ok("release 2026.10") // <1>
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header(HttpHeaders.ETAG, VERSION))
        val notModified = routes.prepare(HttpResponse.notModified<Any>()
            .header(HttpHeaders.ETAG, VERSION))
        routes.GET("/release").respond { direct ->
            if (VERSION == direct.request().header(HttpHeaders.IF_NONE_MATCH)) notModified else current // <2>
        }
    }
}
// end::clazz[]
