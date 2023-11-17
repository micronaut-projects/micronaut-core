package io.micronaut.http.server.netty.shortcircuit

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.RouteExecutor
import io.micronaut.web.router.UriRouteInfo
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

class NettyShortCircuitRouterBuilderSpec extends Specification {
    def 'route builder'(HttpRequest request, @Nullable String methodName) {
        given:
        def ctx = ApplicationContext.run(['spec.name': "NettyShortCircuitRouterBuilderSpec"])
        def scb = new NettyShortCircuitRouterBuilder<UriRouteInfo<?, ?>>()
        ctx.getBean(RouteExecutor).getRouter().collectRoutes(scb)
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
        get("/produces", ['accept': 'application/json'])                     | "produces"
        get("/produces", ['accept': '*/*'])                                  | "produces"
        get("/produces", ['accept': 'text/plain, */*'])                      | "produces"
        get("/produces", ['accept': 'text/plain'])                           | null
        get("/produces-overlap", ['accept': 'text/plain'])                   | "producesOverlapText"
        get("/produces-overlap", ['accept': 'application/json'])             | "producesOverlapJson"
        get("/produces-overlap", ['accept': '*/*'])                          | null
        get("/produces-overlap")                                             | null
        get("/produces-overlap", ['accept': 'text/plain, application/json']) | null
        post("/consumes", ['content-type': 'application/json'])              | "consumes"
        post("/consumes", ['content-type': 'text/plain'])                    | null
        post("/consumes")                                                    | "consumes"
        post("/consumes-overlap", ['content-type': 'application/json'])      | "consumesOverlapJson"
        post("/consumes-overlap", ['content-type': 'text/plain'])            | "consumesOverlapText"
        post("/consumes-overlap")                                            | null
    }

    private static HttpRequest get(String path, Map<String, String> headers = [:]) {
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path)
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.headers().add(entry.key, entry.value)
        }
        return request
    }

    private static HttpRequest post(String path, Map<String, String> headers = [:]) {
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, path)
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.headers().add(entry.key, entry.value)
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
