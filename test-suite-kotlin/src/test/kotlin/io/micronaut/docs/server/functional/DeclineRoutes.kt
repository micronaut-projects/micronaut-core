package io.micronaut.docs.server.functional

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton

private val CACHED = setOf("logo.png", "style.css")

@Requires(property = "spec.name", value = "DirectRoutesTest")
// tag::clazz[]
@Singleton
class CachedAssets : HttpDirectRoutes {
    override fun routes(routes: DirectRouteBuilder) {
        routes.GET("/assets/{name}").respond { direct ->
            if (CACHED.contains(direct.pathVariables().getString("name"))) HttpResponse.ok("cached " + direct.pathVariables().getString("name"))
            else null
        } // <1>
    }
}

// end::clazz[]
@Requires(property = "spec.name", value = "DirectRoutesTest")
// tag::clazz[]
@Singleton
class RenderedAssets : HttpRoutes {
    override fun routes(routes: HttpRouteBuilder) {
        routes.GET("/assets/{name}") { _, pathVariables ->
            HttpResponse.ok("rendered " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)
        } // <2>
    }
}
// end::clazz[]
