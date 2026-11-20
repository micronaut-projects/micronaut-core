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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A running locator sees the captures of its locator route and of the locator routes that
 * located it, see {@link PathVariables#getLocatorRouteCaptures()}.
 */
class LocatorRouteCapturesTest {

    private final List<List<RouteCaptures>> seen = new ArrayList<>();

    private final TestLocatedRoutes<Object> lines = TestLocatedRoutes.of(routes ->
        routes.GET("/{line}", (request, variables) -> {
            // a route handler is not a locator
            assertEquals(List.of(), variables.getLocatorRouteCaptures());
            return HttpResponse.ok();
        }));

    @Test
    void aLocatorSeesTheCapturesOfItsOwnPrefix() {
        Router router = router(null, routes -> routes.locate(t("/:tenant/orders/:id"), this::record, target -> lines));
        String path = "/acme/orders/5/7";
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match);

        List<RouteCaptures> captures = only();
        assertEquals(1, captures.size());
        RouteCaptures own = captures.get(0);
        assertEquals(path, own.path());
        assertEquals("acme", own.value("tenant"));
        assertEquals("5", own.value("id"));
        assertOffsets(own, path, "acme", "5");
        // the same as the match of the located route reports
        assertEquals(match.getLocatorRouteCaptures(), captures);
    }

    @Test
    void aNestedLocatorSeesTheCapturesOfTheOuterLocatorsFirst() {
        TestLocatedRoutes<Object> orders = TestLocatedRoutes.of(routes -> routes.locate(t("/lines/:line"), this::record, target -> lines));
        Router router = router(null, routes -> routes.locate(t("/:tenant/orders/:id"), this::record, target -> orders));
        String path = "/acme/orders/5/lines/y/z";
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match);

        assertEquals(2, seen.size());
        // the outer locator sees its own captures only
        assertEquals(1, seen.get(0).size());
        List<RouteCaptures> nested = seen.get(1);
        assertEquals(2, nested.size());
        assertSame(seen.get(0).get(0), nested.get(0));
        RouteCaptures outer = nested.get(0);
        RouteCaptures inner = nested.get(1);
        assertEquals(path, outer.path());
        assertEquals("/lines/y/z", inner.path());
        assertEquals("y", inner.value("line"));
        assertOffsets(outer, path, "acme", "5");
        // the offsets of the inner captures add to the start of the rest of the outer locator
        int outerRest = outer.start(outer.variables().size() - 1);
        assertEquals("y", path.substring(outerRest - 1 + inner.start(0), outerRest - 1 + inner.end(0)));
        assertEquals(match.getLocatorRouteCaptures(), nested);
    }

    @Test
    void anAsynchronousLocatorSeesTheCapturesOfItsPrefix() {
        CompletableFuture<Object> order = new CompletableFuture<>();
        TestLocatedRoutes<Object> orders = TestLocatedRoutes.of(routes -> routes.locateAsync(t("/lines/:line"), (request, variables) -> {
            seen.add(variables.getLocatorRouteCaptures());
            return order;
        }, target -> lines));
        Router router = router(null, routes -> routes.locateAsync(t("/orders/:id"), (request, variables) -> {
            seen.add(variables.getLocatorRouteCaptures());
            return CompletableFuture.completedFuture("order");
        }, target -> orders));
        HttpRequest<?> request = HttpRequest.GET("/orders/5/lines/y/z");

        RuntimeException pending = assertThrows(RuntimeException.class, () -> router.findClosest(request));
        assertNotNull(RouteLocator.pendingLocation(pending));
        order.complete("line");
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);

        List<RouteCaptures> nested = seen.get(seen.size() - 1);
        assertEquals(2, nested.size());
        assertEquals("5", nested.get(0).value("id"));
        assertEquals("y", nested.get(1).value("line"));
        // matched again when the stage completed: captures of the same paths and values
        assertEquals(describe(match.getLocatorRouteCaptures()), describe(nested));
        assertTrue(seen.stream().allMatch(captures -> !captures.isEmpty()));
    }

    @Test
    void theRawVariablesOfALocatorKeepTheCaptures() {
        Router router = router(null, routes -> routes.locate(t("/orders/:id"), (request, variables) -> record(request, variables.raw()), target -> lines));
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/a%2Fb/7")));
        assertEquals("a%2Fb", only().get(0).value("id"));
    }

    @Test
    void theCapturesOfALocatorInAGroupWithAPrefixAreInTheRequestPath() {
        for (String contextPath : new String[] {null, "/ctx"}) {
            seen.clear();
            String prefix = contextPath == null ? "" : contextPath;
            Router router = router(contextPath, routes -> routes.path("/api", api ->
                api.locate(t("/:version/orders/:id"), this::record, target -> lines)));
            String path = prefix + "/api/v1/orders/5/7";
            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
            assertNotNull(match, path);

            RouteCaptures own = only().get(0);
            assertEquals(path, own.path());
            assertEquals("v1", own.value("version"));
            assertEquals("5", own.value("id"));
            assertOffsets(own, path, "v1", "5");
            assertEquals(match.getLocatorRouteCaptures(), List.of(own));
        }
    }

    @Test
    void theLocatorOfAUriTemplateHasNoCapturesLikeTheMatchOfItsRoutes() {
        Router router = router(null, routes -> routes.locate("/{tenant}/orders/{id}", this::record, target -> lines));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/acme/orders/5/7"));
        assertNotNull(match);
        assertEquals(match.getLocatorRouteCaptures(), only());
    }

    @Test
    void theCapturesOfAPrefixOfAnotherEngineHaveItsOffsets() {
        Router router = router(null, routes ->
            routes.locate(t("/:tenant/orders/:id"), this::record, target -> lines));
        String path = "/acme/orders/5/7";
        assertNotNull(router.findClosest(HttpRequest.GET(path)));
        RouteCaptures own = only().get(0);
        assertTrue(own.hasOffsets());
        assertOffsets(own, path, "acme", "5");
    }

    private Object record(HttpRequest<?> request, PathVariables variables) {
        seen.add(variables.getLocatorRouteCaptures());
        return "target";
    }

    private List<RouteCaptures> only() {
        assertEquals(1, seen.size());
        return seen.get(0);
    }

    private static List<String> describe(List<RouteCaptures> captures) {
        return captures.stream().map(c -> c.path() + " " + c.values()).toList();
    }

    private static RouteTemplate t(String expression) {
        return OffsetColonRouteTemplate.template(expression);
    }

    private static void assertOffsets(RouteCaptures captures, String path, String... values) {
        assertTrue(captures.hasOffsets());
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], path.substring(captures.start(i), captures.end(i)));
        }
    }

    private static Router router(@Nullable String contextPath, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, contextPath);
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
