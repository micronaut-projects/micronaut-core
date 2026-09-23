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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.builder.AsyncContextReplacingRouteResponseFilter;
import io.micronaut.web.router.builder.AsyncContextRouteRequestFilter;
import io.micronaut.web.router.builder.BodyRequestHandler;
import io.micronaut.web.router.builder.ContextReplacingRouteResponseFilter;
import io.micronaut.web.router.builder.ContextRouteRequestFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.ErrorRouteHandler;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.builder.StatusRouteHandler;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
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
        router(routes -> assertEveryMissingSettingFails(routes.GET("/items", HandlerRouteArgumentsTest::ok)));
    }

    @Test
    void aDeclaredRouteRejectsAMissingSettingWhenItIsGivenNotOnEveryRequest() {
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, "/declared/{id}");
        Router router = router(routes -> assertEveryMissingSettingFails(routes.handle(declaration, HandlerRouteArgumentsTest::ok)));

        // nothing invalid was recorded: the route is built and matched
        assertNotNull(router.findClosest(HttpRequest.GET("/declared/5")));
        assertNotNull(router.findClosest(HttpRequest.GET("/declared/5")));
    }

    @Test
    void theBuilderRejectsAMissingArgument() {
        Router router = router(routes -> {
            assertMissing("method", () -> routes.handle((HttpMethod) null, "/x", HandlerRouteArgumentsTest::ok));
            assertMissing("uri", () -> routes.GET(null, HandlerRouteArgumentsTest::ok));
            assertMissing("handler", () -> routes.GET("/x", (RequestHandler) null));
            assertMissing("handler", () -> routes.asyncGET("/x", null));
            assertMissing("bodyType", () -> routes.POST("/x", (Argument<String>) null, (request, pathVariables, body) -> HttpResponse.ok()));
            assertMissing("handler", () -> routes.POST("/x", Argument.of(String.class), (BodyRequestHandler<String>) null));
            assertMissing("httpMethodName", () -> routes.handle((String) null, "/x", HandlerRouteArgumentsTest::ok));
            assertMissing("route", () -> routes.handle((RouteDeclaration) null, HandlerRouteArgumentsTest::ok));
            assertMissing("type", () -> routes.error((Class<IllegalStateException>) null, (request, error) -> HttpResponse.ok()));
            assertMissing("handler", () -> routes.error(IllegalStateException.class, (ErrorRouteHandler<IllegalStateException>) null));
            assertMissing("status", () -> routes.status(null, request -> HttpResponse.ok()));
            assertMissing("handler", () -> routes.status(HttpStatus.NOT_FOUND, (StatusRouteHandler) null));
            assertMissing("methods", () -> routes.handle((Set<HttpMethod>) null, "/x", HandlerRouteArgumentsTest::ok));
            Set<HttpMethod> withNull = new HashSet<>();
            withNull.add(HttpMethod.PUT);
            withNull.add(null);
            assertMissing("methods must not contain null", () -> routes.handle(withNull, "/x", HandlerRouteArgumentsTest::ok));
            IllegalArgumentException noMethod = assertThrows(IllegalArgumentException.class,
                () -> routes.handle(Set.of(), "/x", HandlerRouteArgumentsTest::ok));
            assertEquals("No HTTP method for route: /x", noMethod.getMessage());
            assertMissing("locator", () -> routes.locate("/x", null, target -> RouteTable.empty()));
            assertMissing("tables", () -> routes.locate("/x", (request, pathVariables) -> "target", null));
        });

        // no route was added by a failed declaration
        assertNull(router.findClosest(HttpRequest.GET("/x")));
        assertNull(router.findClosest(HttpRequest.PUT("/x", "")));
    }

    @Test
    void aGroupAndAServerFilterRejectAMissingArgument() {
        router(routes -> {
            routes.group(group -> {
                assertMissing("executorName", () -> group.before((String) null, (ContextRouteRequestFilter) (request, context) -> null));
                assertBlankExecutor(() -> group.before(" ", (ContextRouteRequestFilter) (request, context) -> null));
                assertBlankExecutor(() -> group.afterReplacing("", (ContextReplacingRouteResponseFilter) (request, response, context) -> null));
                assertMissing("filter", () -> group.before((ContextRouteRequestFilter) null));
                assertMissing("condition", () -> group.where(null));
                assertMissing("name", () -> group.attribute(null, "value"));
                assertMissing("value", () -> group.attribute("name", null));
            });
            var filter = routes.filter("/**");
            assertMissing("methods", () -> filter.methods((HttpMethod[]) null));
            assertMissing("methods must not contain null", () -> filter.methods(HttpMethod.GET, null));
            assertMissing("patternStyle", () -> filter.patternStyle(null));
            assertBlankExecutor(() -> filter.before(" ", (ContextRouteRequestFilter) (request, context) -> null));
            assertMissing("filter", () -> filter.beforeAsync((AsyncContextRouteRequestFilter) null));
        });
    }

    @Test
    void aRouteOfACustomMethodIsDeclaredByItsName() {
        Router router = router(routes -> {
            IllegalArgumentException custom = assertThrows(IllegalArgumentException.class,
                () -> routes.handle(HttpMethod.CUSTOM, "/x", HandlerRouteArgumentsTest::ok));
            assertEquals("HttpMethod.CUSTOM is not the name of a method: declare a route of a custom HTTP method by its name, "
                + "e.g. handle(\"PROPFIND\", uri, handler)", custom.getMessage());
            assertThrows(IllegalArgumentException.class, () -> routes.handleAsync(HttpMethod.CUSTOM, "/x", (request, pathVariables) -> null));
            assertThrows(IllegalArgumentException.class, () -> routes.handle(Set.of(HttpMethod.GET, HttpMethod.CUSTOM), "/x", HandlerRouteArgumentsTest::ok));
            for (String name : new String[] {"", " ", "PROP FIND", "GET\r\n", "PROP/FIND"}) {
                IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                    () -> routes.handle(name, "/x", HandlerRouteArgumentsTest::ok), name);
                assertEquals("The name of an HTTP method must be a token, e.g. PROPFIND: '" + name + "'", invalid.getMessage());
            }
            routes.handle("PROPFIND", "/x", HandlerRouteArgumentsTest::ok);
        });

        // the rejected declarations added no route: GET /x is not routed, PROPFIND /x is
        assertNull(router.findClosest(HttpRequest.GET("/x")));
        assertEquals(1, router.findAny(HttpRequest.create(HttpMethod.CUSTOM, "/x", "PROPFIND")).size());
    }

    @Test
    void aDeclarationOfACustomMethodIsDeclaredByItsName() {
        IllegalArgumentException custom = assertThrows(IllegalArgumentException.class, () -> RouteDeclaration.of(HttpMethod.CUSTOM, "/x"));
        assertEquals("HttpMethod.CUSTOM is not the name of a method: declare a route of a custom HTTP method by its name, "
            + "e.g. RouteDeclaration.of(\"PROPFIND\", uriTemplate)", custom.getMessage());
        assertThrows(IllegalArgumentException.class, () -> IndexedRouteDeclaration.of(HttpMethod.CUSTOM, "/x"));
        assertMissing("httpMethod", () -> IndexedRouteDeclaration.of((HttpMethod) null, "/x"));
        assertMissing("httpMethodName", () -> IndexedRouteDeclaration.of((String) null, "/x"));
        assertMissing("uriTemplate", () -> RouteDeclaration.of(HttpMethod.GET, null));
        assertThrows(IllegalArgumentException.class, () -> RouteDeclaration.of(" ", "/x"));
        assertThrows(IllegalArgumentException.class, () -> RouteDeclaration.of("", "/x"));
        RouteDeclaration propfind = RouteDeclaration.of("PROPFIND", "/x");
        assertEquals(HttpMethod.CUSTOM, propfind.httpMethod());
        assertEquals("PROPFIND", propfind.httpMethodName());
        assertEquals("GET", RouteDeclaration.of("get", "/x").httpMethodName());
    }

    private static void assertEveryMissingSettingFails(HttpRouteSpec route) {
        assertMissing("mediaTypes", () -> route.consumes((MediaType[]) null));
        assertMissing("mediaTypes must not contain null", () -> route.consumes(MediaType.TEXT_PLAIN_TYPE, null));
        assertMissing("mediaTypes", () -> route.produces((MediaType[]) null));
        assertMissing("mediaTypes must not contain null", () -> route.produces((MediaType) null));
        assertMissing("annotationMetadata", () -> route.annotationMetadata(null));
        assertMissing("method", () -> route.implementing((ExecutableMethod<?, ?>) null));
        assertMissing("executorName", () -> route.executeOn(null));
        assertBlankExecutor(() -> route.executeOn(""));
        assertBlankExecutor(() -> route.executeOn("  "));
        assertMissing("filter", () -> route.before((ContextRouteRequestFilter) null));
        assertMissing("executorName", () -> route.before(null, (ContextRouteRequestFilter) (request, context) -> null));
        assertBlankExecutor(() -> route.before(" ", (ContextRouteRequestFilter) (request, context) -> null));
        assertMissing("filter", () -> route.before("blocking", (ContextRouteRequestFilter) null));
        assertMissing("filter", () -> route.beforeAsync((AsyncContextRouteRequestFilter) null));
        assertMissing("filter", () -> route.afterReplacing((ContextReplacingRouteResponseFilter) null));
        assertMissing("executorName", () -> route.afterReplacing(null, (ContextReplacingRouteResponseFilter) (request, response, context) -> null));
        assertMissing("filter", () -> route.afterReplacing("blocking", (ContextReplacingRouteResponseFilter) null));
        assertMissing("filter", () -> route.afterReplacingAsync((AsyncContextReplacingRouteResponseFilter) null));
        assertMissing("condition", () -> route.where(null));
        assertMissing("name", () -> route.attribute(null, "value"));
        assertMissing("value", () -> route.attribute("name", null));
        // the route is still usable
        route.consumes(MediaType.APPLICATION_JSON_TYPE).annotationMetadata(AnnotationMetadata.EMPTY_METADATA);
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
