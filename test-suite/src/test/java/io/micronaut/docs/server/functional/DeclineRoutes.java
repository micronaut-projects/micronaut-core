package io.micronaut.docs.server.functional;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.util.Set;

public class DeclineRoutes {

    private static final Set<String> CACHED = Set.of("logo.png", "style.css");

    @Requires(property = "spec.name", value = "DirectRoutesTest")
    // tag::clazz[]
    @Singleton
    static class CachedAssets implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/assets/{name}").respond(direct -> CACHED.contains(direct.pathVariables().getString("name"))
                ? HttpResponse.ok("cached " + direct.pathVariables().getString("name"))
                : null); // <1>
        }
    }

    // end::clazz[]
    @Requires(property = "spec.name", value = "DirectRoutesTest")
    // tag::clazz[]
    @Singleton
    static class RenderedAssets implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/assets/{name}", (request, pathVariables) ->
                HttpResponse.ok("rendered " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)); // <2>
        }
    }
    // end::clazz[]
}
