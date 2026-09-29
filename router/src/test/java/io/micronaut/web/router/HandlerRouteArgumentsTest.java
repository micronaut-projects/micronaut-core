/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.AsyncBodyRequestHandler;
import io.micronaut.web.router.builder.AsyncContextReplacingRouteResponseFilter;
import io.micronaut.web.router.builder.AsyncContextReplacingRouteRequestFilter;
import io.micronaut.web.router.builder.AsyncContextRouteRequestFilter;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.AsyncRouteRequestFilter;
import io.micronaut.web.router.builder.BodyRequestHandler;
import io.micronaut.web.router.builder.ContextReplacingRouteResponseFilter;
import io.micronaut.web.router.builder.ContextReplacingRouteRequestFilter;
import io.micronaut.web.router.builder.ContextRouteRequestFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.ErrorRouteHandler;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;

import io.micronaut.web.router.builder.RouteRequestFilter;
import io.micronaut.web.router.builder.StatusRouteHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The handler route builder rejects a missing argument when it is given, with its name, instead
 * of failing when the route is built or on every request.
 */
class HandlerRouteArgumentsTest {

    @Test
    void aRouteRejectsAMissingSetting() {
        router(routes -> {
            HttpRouteSpec route = routes.GET("/items");
            assertEveryMissingSettingFails(route);
            route.handle(HandlerRouteArgumentsTest::ok);
        });
    }

    @Test
    void aRouteByMethodRejectsAMissingSettingWhenItIsGivenNotOnEveryRequest() {
        Router router = router(routes -> {
            HttpRouteSpec route = routes.route(HttpMethod.GET, "/declared/{id}");
            assertEveryMissingSettingFails(route);
            route.handle(HandlerRouteArgumentsTest::ok);
        });

        // nothing invalid was recorded: the route is built and matched
        assertNotNull(router.findClosest(HttpRequest.GET("/declared/5")));
        assertNotNull(router.findClosest(HttpRequest.GET("/declared/5")));
    }

    @Test
    void theBuilderRejectsAMissingArgument() {
        Router router = router(routes -> {
            assertMissing("method", () -> routes.route((HttpMethod) null, "/x"));
            assertMissing("uri", () -> routes.GET(null, HandlerRouteArgumentsTest::ok));
            assertMissing("handler", () -> routes.GET("/x", (RequestHandler) null));
            assertMissing("handler", () -> routes.GET("/x").handleAsync((AsyncRequestHandler) null));
            assertMissing("handler", () -> routes.POST("/x").body().handleAsync((AsyncBodyRequestHandler) null));
            HttpRouteSpec body = routes.POST("/body");
            assertMissing("bodyType", () -> body.body((Argument<String>) null));
            // the route stays usable
            body.handle(HandlerRouteArgumentsTest::ok);
            assertMissing("handler", () -> routes.POST("/x").body(Argument.of(String.class)).handle((BodyRequestHandler<String>) null));
            assertMissing("httpMethodName", () -> routes.route((String) null, "/x"));
            assertMissing("response", () -> routes.GET("/x").respond((HttpResponse<?>) null));
            assertMissing("type", () -> routes.error((Class<IllegalStateException>) null, (request, error) -> HttpResponse.ok()));
            assertMissing("handler", () -> routes.error(IllegalStateException.class, (ErrorRouteHandler<IllegalStateException>) null));
            assertMissing("status", () -> routes.status(null, request -> HttpResponse.ok()));
            assertMissing("handler", () -> routes.status(HttpStatus.NOT_FOUND, (StatusRouteHandler) null));
            assertMissing("methods", () -> routes.route((Set<HttpMethod>) null, "/x"));
            Set<HttpMethod> withNull = new HashSet<>();
            withNull.add(HttpMethod.PUT);
            withNull.add(null);
            assertMissing("methods must not contain null", () -> routes.route(withNull, "/x"));
            IllegalArgumentException noMethod = assertThrows(IllegalArgumentException.class,
                () -> routes.route(Set.of(), "/x").handle(HandlerRouteArgumentsTest::ok));
            assertEquals("No HTTP method for route: /x", noMethod.getMessage());
            assertMissing("locator", () -> routes.locate("/x", null, target -> null));
            assertMissing("routesOf", () -> routes.locate("/x", (request, pathVariables) -> "target",
                (java.util.function.Function<Object, io.micronaut.web.router.builder.LocatedRoutes<?>>) null));
            assertMissing("routes", () -> routes.locate("/x", (request, pathVariables) -> "target",
                (io.micronaut.web.router.builder.LocatedRoutes<Object>) null));
        });

        // no route was added by a failed declaration
        assertNull(router.findClosest(HttpRequest.GET("/x")));
        assertNull(router.findClosest(HttpRequest.PUT("/x", "")));
    }

    @Test
    void aGroupAndAServerFilterRejectAMissingArgument() {
        router(routes -> {
            routes.group(group -> {
                assertMissing("executorName", () -> group.beforeReplacing((ContextReplacingRouteRequestFilter) (request, context) -> null).executeOn(null));
                assertBlankExecutor(() -> group.beforeReplacing((ContextReplacingRouteRequestFilter) (request, context) -> null).executeOn(" "));
                assertBlankExecutor(() -> group.afterReplacing((ContextReplacingRouteResponseFilter) (request, response, context) -> null).executeOn(""));
                assertMissing("filter", () -> group.beforeReplacing((ContextReplacingRouteRequestFilter) null));
                assertMissing("condition", () -> group.where(null));
                assertMissing("name", () -> group.attribute(null, "value"));
                assertMissing("value", () -> group.attribute("name", null));
            });
            var filter = routes.filter("/**");
            assertMissing("methods", () -> filter.methods((HttpMethod[]) null));
            assertMissing("methods must not contain null", () -> filter.methods(HttpMethod.GET, null));
            assertMissing("patternStyle", () -> filter.patternStyle(null));
            assertBlankExecutor(() -> filter.beforeReplacing((ContextReplacingRouteRequestFilter) (request, context) -> null).executeOn(" "));
            assertMissing("filter", () -> filter.beforeReplacingAsync((AsyncContextReplacingRouteRequestFilter) null));
        });
    }

    @Test
    void aRouteOfACustomMethodIsDeclaredByItsName() {
        Router router = router(routes -> {
            IllegalArgumentException custom = assertThrows(IllegalArgumentException.class,
                () -> routes.route(HttpMethod.CUSTOM, "/x").handle(HandlerRouteArgumentsTest::ok));
            assertEquals("HttpMethod.CUSTOM is not the name of a method: declare a route of a custom HTTP method by its name, "
                + "e.g. route(\"PROPFIND\", uri)", custom.getMessage());
            assertThrows(IllegalArgumentException.class, () -> routes.route(HttpMethod.CUSTOM, "/x").handleAsync((request, pathVariables) -> null));
            assertThrows(IllegalArgumentException.class, () -> routes.route(Set.of(HttpMethod.GET, HttpMethod.CUSTOM), "/x").handle(HandlerRouteArgumentsTest::ok));
            for (String name : new String[] {"", " ", "PROP FIND", "GET\r\n", "PROP/FIND"}) {
                IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                    () -> routes.route(name, "/x"), name);
                assertEquals("The name of an HTTP method must be a token, e.g. PROPFIND: '" + name + "'", invalid.getMessage());
            }
            routes.route("PROPFIND", "/x").handle(HandlerRouteArgumentsTest::ok);
        });

        // the rejected declarations added no route: GET /x is not routed, PROPFIND /x is
        assertNull(router.findClosest(HttpRequest.GET("/x")));
        assertEquals(1, router.findAny(HttpRequest.create(HttpMethod.CUSTOM, "/x", "PROPFIND")).size());
    }

    @Test
    void aRouteByTheNameOfAStandardMethodIsARouteOfThatMethod() {
        Router router = router(routes -> {
            assertMissing("uri", () -> routes.route("PROPFIND", (String) null));
            routes.route("get", "/x").handle(HandlerRouteArgumentsTest::ok);
            routes.route("PROPFIND", "/y").handle(HandlerRouteArgumentsTest::ok);
        });

        UriRouteMatch<Object, Object> get = router.findClosest(HttpRequest.GET("/x"));
        assertNotNull(get);
        assertEquals(HttpMethod.GET, get.getRouteInfo().getHttpMethod());
        assertEquals("GET", get.getRouteInfo().getHttpMethodName());
        UriRouteMatch<Object, Object> propfind = router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, "/y", "PROPFIND"));
        assertNotNull(propfind);
        assertEquals(HttpMethod.CUSTOM, propfind.getRouteInfo().getHttpMethod());
        assertEquals("PROPFIND", propfind.getRouteInfo().getHttpMethodName());
    }

    private static void assertEveryMissingSettingFails(HttpRouteSpec route) {
        assertMissing("mediaTypes", () -> route.consumes((MediaType[]) null));
        assertMissing("mediaTypes must not contain null", () -> route.consumes(MediaType.TEXT_PLAIN_TYPE, null));
        assertMissing("mediaTypes", () -> route.produces((MediaType[]) null));
        assertMissing("mediaTypes must not contain null", () -> route.produces((MediaType) null));
        assertMissing("annotationMetadata", () -> route.annotationMetadata(null));
        assertMissing("executorName", () -> route.executeOn(null));
        assertBlankExecutor(() -> route.executeOn(""));
        assertBlankExecutor(() -> route.executeOn("  "));
        assertMissing("filter", () -> route.beforeReplacing((ContextReplacingRouteRequestFilter) null));
        assertMissing("executorName", () -> route.beforeReplacing((ContextReplacingRouteRequestFilter) (request, context) -> null).executeOn(null));
        assertBlankExecutor(() -> route.beforeReplacing((ContextReplacingRouteRequestFilter) (request, context) -> null).executeOn(" "));
        assertMissing("filter", () -> route.beforeReplacing((ContextReplacingRouteRequestFilter) null).executeOn("blocking"));
        assertMissing("filter", () -> route.beforeReplacingAsync((AsyncContextReplacingRouteRequestFilter) null));
        assertMissing("filter", () -> route.before((ContextRouteRequestFilter) null));
        assertMissing("filter", () -> route.before((RouteRequestFilter) null));
        assertMissing("executorName", () -> route.before((RouteRequestFilter) request -> { }).executeOn(null));
        assertBlankExecutor(() -> route.before((ContextRouteRequestFilter) (request, context) -> { }).executeOn(" "));
        assertMissing("filter", () -> route.beforeAsync((AsyncRouteRequestFilter) null));
        assertMissing("filter", () -> route.beforeAsync((AsyncContextRouteRequestFilter) null));
        assertMissing("filter", () -> route.afterReplacing((ContextReplacingRouteResponseFilter) null));
        assertMissing("executorName", () -> route.afterReplacing((ContextReplacingRouteResponseFilter) (request, response, context) -> null).executeOn(null));
        assertMissing("filter", () -> route.afterReplacing((ContextReplacingRouteResponseFilter) null).executeOn("blocking"));
        assertMissing("filter", () -> route.afterReplacingAsync((AsyncContextReplacingRouteResponseFilter) null));
        assertMissing("condition", () -> route.where(null));
        assertMissing("name", () -> route.attribute(null, "value"));
        assertMissing("value", () -> route.attribute("name", null));
        // the route is still usable
        route.consumes(MediaType.APPLICATION_JSON_TYPE).annotationMetadata(new AnnotationMetadataProvider() { });
    }

    private static void assertMissing(String name, Executable call) {
        NullPointerException e = assertThrows(NullPointerException.class, call);
        assertEquals(name, e.getMessage());
    }

    private static void assertBlankExecutor(Executable call) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
        assertEquals("The name of an executor must not be blank", e.getMessage());
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
