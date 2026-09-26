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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The path an engine matches its routes against, see
 * {@link io.micronaut.http.uri.spi.RouteTemplateEngine#matchingPath(String)}: the matrix
 * parameters of the colon engine of the tests, which the routes ignore while the request keeps
 * its raw URI.
 */
class RouteTemplateEngineMatchingPathTest {

    private static final RouteTemplate CARS = MatrixColonRouteTemplateEngine.template("/cars/:id/details");

    @Test
    void matrixParametersOfAMiddleLiteralSegmentDoNotAffectMatching() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, CARS), (request, variables) -> HttpResponse.ok()));
        // the literal segment the index prunes by carries the matrix parameters
        UriRouteMatch<Object, Object> match = findClosest(router, "/cars;color=red/7/details");
        assertEquals(CARS, match.getRouteInfo().getRouteTemplate());
        assertEquals("7", match.getVariableValues().get("id"));
        assertNotNull(findClosestOrNull(router, "/cars/7/details"));
        assertNull(findClosestOrNull(router, "/cars/7/other"));
    }

    @Test
    void aCapturedVariableExcludesTheMatrixParameters() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, CARS), (request, variables) -> HttpResponse.ok()));
        UriRouteMatch<Object, Object> match = findClosest(router, "/cars;color=red/7;trim=gt/details");
        assertEquals("7", match.getVariableValues().get("id"));
        // the request is never changed
        HttpRequest<?> request = HttpRequest.GET("/cars;color=red/7;trim=gt/details");
        assertEquals("/cars;color=red/7;trim=gt/details", request.getPath());
        assertNotNull(router.findClosest(request));
        assertEquals("/cars;color=red/7;trim=gt/details", request.getPath());
    }

    @Test
    void matrixParametersOfTheLastSegmentDoNotAffectMatching() {
        RouteTemplate items = MatrixColonRouteTemplateEngine.template("/items/:id");
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, items), (request, variables) -> HttpResponse.ok()));
        assertEquals("5", findClosest(router, "/items/5;color=red").getVariableValues().get("id"));
        assertEquals(items, findClosest(router, "/items;v=1/5;color=red").getRouteInfo().getRouteTemplate());
        // a matrix parameter does not make a segment of its own: the trailing slash is ignored,
        // as it is for every route
        assertNotNull(findClosestOrNull(router, "/items/5/;color=red"));
        assertNull(findClosestOrNull(router, "/items/5/6;color=red"));
    }

    @Test
    void theContextPathMayCarryMatrixParameters() {
        Router router = RouteTemplateEngineOrderTest.router("/ctx", routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, CARS), (request, variables) -> HttpResponse.ok()));
        assertEquals(MatrixColonRouteTemplateEngine.template("/ctx/cars/:id/details"),
            findClosest(router, "/ctx;v=1/cars;color=red/7/details").getRouteInfo().getRouteTemplate());
        assertEquals("7", findClosest(router, "/ctx/cars/7;trim=gt/details").getVariableValues().get("id"));
    }

    @Test
    void aLocatorPrefixMaySpanAMatrixSegment() {
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, MatrixColonRouteTemplateEngine.template("/items/:item")),
                (request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(MatrixColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> items));
        // the rest of the path the located table matches comes from the path the engine matches
        UriRouteMatch<Object, Object> match = findClosest(router, "/orders;v=1/5;trim=gt/items;x=1/3;y=2");
        assertEquals(MatrixColonRouteTemplateEngine.template("/items/:item"), match.getRouteInfo().getRouteTemplate());
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals("3", match.getVariableValues().get("item"));
    }

    @Test
    void micronautRoutesOfTheSameRouterAreNotChanged() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, MatrixColonRouteTemplateEngine.template("/a")), (request, variables) -> HttpResponse.ok());
            routes.GET("/b", (request, variables) -> HttpResponse.ok());
        });
        assertNotNull(findClosestOrNull(router, "/a;b"));
        assertNotNull(findClosestOrNull(router, "/b"));
        // a Micronaut route matches the raw path only
        assertNull(findClosestOrNull(router, "/b;c"));
        assertTrue(router.findAny(HttpRequest.GET("/b;c")).isEmpty());
        // a Micronaut route under a path of the engine is matched with the raw path only
        Router both = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.GET, MatrixColonRouteTemplateEngine.template("/a")), (request, variables) -> HttpResponse.ok());
            routes.GET("/a/inner", (request, variables) -> HttpResponse.ok());
        });
        assertEquals(MatrixColonRouteTemplateEngine.template("/a"), findClosest(both, "/a;x=1").getRouteInfo().getRouteTemplate());
        assertEquals(RouteTemplate.micronaut("/a/inner"), findClosest(both, "/a/inner").getRouteInfo().getRouteTemplate());
        assertNull(findClosestOrNull(both, "/a;x=1/inner"));
    }

    @Test
    void theAllowedMethodsAreFoundWithTheMatchingPath() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.handle(RouteDeclaration.of(HttpMethod.POST, CARS), (request, variables) -> HttpResponse.ok());
            routes.handle(RouteDeclaration.of(HttpMethod.PUT, CARS), (request, variables) -> HttpResponse.ok());
        });
        assertNull(findClosestOrNull(router, "/cars;color=red/7/details"));
        List<HttpMethod> methods = router.<Object, Object>findAny(HttpRequest.GET("/cars;color=red/7/details")).stream()
            .map(m -> m.getRouteInfo().getHttpMethod())
            .sorted()
            .toList();
        assertEquals(List.of(HttpMethod.POST, HttpMethod.PUT), methods);
    }

    @Test
    void theMediaTypesAreNegotiatedWithTheMatchingPath() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.handle(RouteDeclaration.of(HttpMethod.GET, CARS), (request, variables) -> HttpResponse.ok())
                .produces(MediaType.APPLICATION_JSON_TYPE));
        HttpRequest<?> acceptable = HttpRequest.GET("/cars;color=red/7/details").accept(MediaType.APPLICATION_JSON_TYPE);
        assertNotNull(router.findClosest(acceptable));
        HttpRequest<?> unacceptable = HttpRequest.GET("/cars;color=red/7/details").accept(MediaType.TEXT_HTML_TYPE);
        assertNull(router.findClosest(unacceptable));
        // the route is found for the 406, with the matrix parameters: the GET route and its implicit HEAD route
        List<RouteTemplate> found = router.findAny(unacceptable).stream().map(m -> m.getRouteInfo().getRouteTemplate()).toList();
        assertEquals(List.of(CARS, CARS), found);
    }

    @Test
    void thePathWithoutMatrixParametersIsTheSameInstance() {
        MatrixColonRouteTemplateEngine engine = new MatrixColonRouteTemplateEngine();
        String path = "/cars/7/details";
        assertEquals(path, engine.matchingPath(path));
        assertTrue(path == engine.matchingPath(path), "the same instance: the router then does no extra work");
        // idempotent: the rest of a path it returned may be given to it again
        String stripped = engine.matchingPath("/cars;color=red/7;trim=gt/details");
        assertEquals("/cars/7/details", stripped);
        assertEquals(stripped, engine.matchingPath(stripped));
    }

    private static UriRouteMatch<Object, Object> findClosest(Router router, String path) {
        UriRouteMatch<Object, Object> match = findClosestOrNull(router, path);
        assertNotNull(match, path);
        return match;
    }

    private static @Nullable UriRouteMatch<Object, Object> findClosestOrNull(Router router, String path) {
        return router.findClosest(HttpRequest.GET(path));
    }
}
