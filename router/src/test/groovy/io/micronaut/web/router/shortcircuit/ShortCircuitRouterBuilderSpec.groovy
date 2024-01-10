package io.micronaut.web.router.shortcircuit

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.web.router.DefaultRouter
import io.micronaut.web.router.UriRouteInfo
import spock.lang.Specification

class ShortCircuitRouterBuilderSpec extends Specification {
    def 'route builder'(HttpRequest<?> request, @Nullable String methodName) {
        given:
        def ctx = ApplicationContext.run(['spec.name': "NettyShortCircuitRouterBuilderSpec"])
        def scb = new ShortCircuitRouterBuilder<UriRouteInfo<?, ?>>()
        ctx.getBean(DefaultRouter).collectRoutes(scb)
        def plan = scb.plan()

        when:
        def leaf = plan.execute(request)
        def actualName
        if (leaf instanceof ExecutionLeaf.Route) {
            actualName = ((UriRouteInfo<?, ?>) leaf.routeMatch()).targetMethod.name
        } else {
            actualName = null
        }
        then:
        actualName == methodName

        where:
        request                                                              | methodName
        get("/simple")                                                       | "simple"
        get("/pattern-collision/exact")                                      | null
        get("/produces")                                                     | "produces"
        get("/produces", ['Accept': 'application/json'])                     | "produces"
        get("/produces", ['Accept': '*/*'])                                  | "produces"
        get("/produces", ['Accept': 'text/plain, */*'])                      | "produces"
        get("/produces", ['Accept': 'text/plain'])                           | null
        get("/produces-overlap", ['Accept': 'text/plain'])                   | "producesOverlapText"
        get("/produces-overlap", ['Accept': 'application/json'])             | "producesOverlapJson"
        get("/produces-overlap", ['Accept': '*/*'])                          | null
        get("/produces-overlap")                                             | null
        get("/produces-overlap", ['Accept': 'text/plain, application/json']) | null
        post("/consumes", ['Content-Type': 'application/json'])              | "consumes"
        post("/consumes", ['Content-Type': 'text/plain'])                    | null
        post("/consumes")                                                    | "consumes"
        post("/consumes-overlap", ['Content-Type': 'application/json'])      | "consumesOverlapJson"
        post("/consumes-overlap", ['Content-Type': 'text/plain'])            | "consumesOverlapText"
        post("/consumes-overlap")                                            | null
    }

    private static HttpRequest<?> get(String path, Map<String, String> headers = [:]) {
        def request = HttpRequest.GET(path)
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.getHeaders().add(entry.key, entry.value)
        }
        return request
    }

    private static HttpRequest<?> post(String path, Map<String, String> headers = [:]) {
        def request = HttpRequest.POST(path, null)
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.getHeaders().add(entry.key, entry.value)
        }
        return request
    }

    @Controller
    @Requires(property = "spec.name", value = "NettyShortCircuitRouterBuilderSpec")
    static class TestBean {
        @Get("/simple")
        void simple() {
        }

        @Get("/pattern-collision/{pattern}")
        void patternCollisionPattern() {
        }

        @Get("/pattern-collision/exact")
        void patternCollisionExact() {
        }

        @Get("/produces")
        @Produces(MediaType.APPLICATION_JSON)
        void produces() {
        }

        @Get("/produces-overlap")
        @Produces(MediaType.APPLICATION_JSON)
        void producesOverlapJson() {
        }

        @Get("/produces-overlap")
        @Produces(MediaType.TEXT_PLAIN)
        void producesOverlapText() {
        }

        @Post("/consumes")
        @Consumes(MediaType.APPLICATION_JSON)
        void consumes() {
        }

        @Post("/consumes-overlap")
        @Consumes(MediaType.APPLICATION_JSON)
        void consumesOverlapJson() {
        }

        @Post("/consumes-overlap")
        @Consumes(MediaType.TEXT_PLAIN)
        void consumesOverlapText() {
        }
    }
}
