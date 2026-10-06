package io.micronaut.docs.server.functional

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.direct.DirectContext
import io.micronaut.web.router.direct.DirectRouteBuilder
import io.micronaut.web.router.direct.HttpDirectRoutes
import jakarta.inject.Singleton

import java.util.function.Function

@Requires(property = "spec.name", value = "DirectRoutesSpec")
// tag::clazz[]
@Singleton
class CachedAssets implements HttpDirectRoutes {
    private static final Set<String> CACHED = ["logo.png", "style.css"] as Set

    @Override
    void routes(DirectRouteBuilder routes) {
        routes.GET("/assets/{name}").respond({ DirectContext direct ->
            CACHED.contains(direct.pathVariables().getString("name"))
                ? direct.responses().ok("cached " + direct.pathVariables().getString("name"))
                : null
        } as Function<DirectContext, HttpResponse<?>>) // <1>
    }
}

// end::clazz[]
@Requires(property = "spec.name", value = "DirectRoutesSpec")
// tag::clazz[]
@Singleton
class RenderedAssets implements HttpRoutes {
    @Override
    void routes(HttpRouteBuilder routes) {
        routes.GET("/assets/{name}") { request, pathVariables ->
            HttpResponse.ok("rendered " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)
        } // <2>
    }
}
// end::clazz[]
