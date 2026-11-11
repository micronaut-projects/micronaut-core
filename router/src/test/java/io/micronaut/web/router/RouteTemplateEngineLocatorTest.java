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
import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateParser;
import io.micronaut.http.uri.RouteTemplateVariable;
import io.micronaut.http.uri.UriRouteTemplate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locator routes whose prefix is a template of another engine, and located tables with routes of
 * another engine.
 */
class RouteTemplateEngineLocatorTest {

    private final List<Map<String, Object>> located = new ArrayList<>();

    private final TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes -> {
        routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok());
        routes.GET("/", (request, variables) -> HttpResponse.ok());
        routes.GET("/{+rest}", (request, variables) -> HttpResponse.ok());
    });

    @Test
    void aLanguageThatMatchesAPrefixInOnePassIsMatchedOncePerLocatorRoute() {
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger prefixMatches = new AtomicInteger();
        RouteTemplateParser parser = expression -> new OnePassPrefix(ColonRouteTemplateEngine.template(expression), matches, prefixMatches);
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group ->
            group.templates(parser).locate("/:tenant/orders/:id", (request, variables) -> "order", target -> items)));

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/acme/orders/5/items/3"));
        assertNotNull(match);
        assertEquals("acme", match.getVariableValues().get("tenant"));
        assertEquals("3", match.getVariableValues().get("item"));
        // the prefix found where its match ends: the template matched once, not at every slash
        assertEquals(prefixMatches.get(), matches.get());

        // a long path of short segments that the prefix does not match
        matches.set(0);
        prefixMatches.set(0);
        assertNull(router.findClosest(HttpRequest.GET("/acme" + "/s".repeat(5_000))));
        assertTrue(matches.get() <= prefixMatches.get(), matches + " matches for " + prefixMatches + " prefix matches");
    }

    @Test
    void theDefaultPrefixMatchOfALanguageFindsTheLocatorRouteOfALongPath() {
        TestLocatedRoutes<Object> rest = TestLocatedRoutes.of(routes ->
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/:a/:b")).handle((request, variables) -> HttpResponse.ok()));
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> routes.group(group ->
            group.templates(ColonRouteTemplateEngine.INSTANCE).locate("/:tenant/orders", (request, variables) -> "order", target -> rest)));
        assertNotNull(router.findClosest(HttpRequest.GET("/acme/orders/1/2")));
        // the default tries the prefixes before each slash: a long unmatched path is not found
        assertNull(router.findClosest(HttpRequest.GET("/acme" + "/s".repeat(5_000))));
    }

    @Test
    void aPrefixOfAnotherEngineLocatesTheRestOfThePath() {
        for (String contextPath : new String[] {null, "/ctx"}) {
            String prefix = contextPath == null ? "" : contextPath;
            Router router = RouteTemplateEngineOrderTest.router(contextPath, routes ->
                routes.group(g -> g.templates(ColonRouteTemplateEngine.INSTANCE).locate("/orders/:id", (request, variables) -> {
                    located.add(Map.of("id", variables.getString("id")));
                    return "order-" + variables.getString("id");
                }, target -> items)));

            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(prefix + "/orders/5/items/3"));
            assertNotNull(match, prefix);
            assertEquals(ColonRouteTemplateEngine.template("/items/:item"), match.getRouteInfo().getRouteTemplate(), prefix);
            assertEquals("5", match.getVariableValues().get("id"), prefix);
            assertEquals("3", match.getVariableValues().get("item"), prefix);
            assertEquals(Map.of("id", "5"), located.get(located.size() - 1), prefix);

            // the prefix alone, and with a trailing slash, locates the root of the table
            assertEquals(UriRouteTemplate.of("/"), router.findClosest(HttpRequest.GET(prefix + "/orders/5")).getRouteInfo().getRouteTemplate(), prefix);
            assertEquals(UriRouteTemplate.of("/"), router.findClosest(HttpRequest.GET(prefix + "/orders/5/")).getRouteInfo().getRouteTemplate(), prefix);
            // the rest of the path is matched by the table, with its own engines
            UriRouteMatch<Object, Object> rest = router.findClosest(HttpRequest.GET(prefix + "/orders/5/a/b"));
            assertEquals(UriRouteTemplate.of("/{+rest}"), rest.getRouteInfo().getRouteTemplate(), prefix);
            assertEquals("a/b", rest.getVariableValues().get("rest"), prefix);
            // a path the prefix does not match is not located
            int before = located.size();
            assertNull(router.findClosest(HttpRequest.GET(prefix + "/orders")), prefix);
            assertNull(router.findClosest(HttpRequest.GET(prefix + "/other/5/items/3")), prefix);
            assertEquals(before, located.size(), prefix);
        }
    }

    @Test
    void theLocatorRoutesOfAnEngineAnswerEveryMethod() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.group(g -> g.templates(ColonRouteTemplateEngine.INSTANCE).locate("/orders/:id", (request, variables) -> "order", target -> items)));
        List<HttpMethod> methods = router.uriRoutes()
            .filter(r -> !(r.getRouteTemplate() instanceof UriRouteTemplate))
            .map(UriRouteInfo::getHttpMethod)
            .sorted()
            .toList();
        // a route per standard method, and one of the custom methods
        assertEquals(List.of(HttpMethod.values()), methods);
    }

    @Test
    void aLiteralSegmentAfterTheLocatorIsNotPartOfThePrefix() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.group(g -> g.templates(ColonRouteTemplateEngine.INSTANCE).locate("/orders/:id", (request, variables) -> "order", target -> items));
            // more literal text: selected over the locator
            routes.route(HttpMethod.GET, ColonRouteTemplateEngine.template("/orders/:id/summary")).handle((request, variables) -> HttpResponse.ok());
        });
        assertEquals(ColonRouteTemplateEngine.template("/orders/:id/summary"),
            router.findClosest(HttpRequest.GET("/orders/5/summary")).getRouteInfo().getRouteTemplate());
        assertEquals(ColonRouteTemplateEngine.template("/items/:item"),
            router.findClosest(HttpRequest.GET("/orders/5/items/1")).getRouteInfo().getRouteTemplate());
    }

    @Test
    void anAsynchronousLocatorOfAnotherEngineLocatesWhenItsStageCompletes() {
        java.util.concurrent.CompletableFuture<Object> order = new java.util.concurrent.CompletableFuture<>();
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.group(g -> g.templates(ColonRouteTemplateEngine.INSTANCE).locateAsync("/orders/:id", (request, variables) -> order, target -> items));
            routes.locateAsync("/done/{id}", (request, variables) ->
                java.util.concurrent.CompletableFuture.completedFuture("done"), target -> items);
        });
        HttpRequest<?> request = HttpRequest.GET("/orders/5/items/3");
        RuntimeException pending = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> router.findClosest(request));
        java.util.concurrent.CompletionStage<?> stage = RouteLocator.pendingLocation(pending);
        assertNotNull(stage);
        order.complete("order-5");
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        assertEquals(ColonRouteTemplateEngine.template("/items/:item"), match.getRouteInfo().getRouteTemplate());
        assertEquals("5", match.getVariableValues().get("id"));
        assertNotNull(router.findClosest(HttpRequest.GET("/done/1/items/3")));
    }

    @Test
    void aMicronautTemplateIsTheSameAsTheStringPrefix() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate("/orders/{id}", (request, variables) -> "order", target -> items));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/items/3"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals(UriRouteTemplate.of("/orders/{id}/{+" + RouteLocator.REMAINDER + "}"),
            router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.GET && r.getRouteTemplate().expression().contains("+"))
                .findFirst().orElseThrow().getRouteTemplate());
    }

    @Test
    void aLocatedTableLocatesAgainWithAnotherEngine() {
        TestLocatedRoutes<Object> orders = TestLocatedRoutes.of(routes ->
            routes.group(g -> g.templates(ColonRouteTemplateEngine.INSTANCE).locate("/lines/:line", (request, variables) -> "line", target -> items)));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.group(g -> g.templates(ColonRouteTemplateEngine.INSTANCE).locate("/orders/:id", (request, variables) -> "order", target -> orders)));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/lines/2/items/3"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals("2", match.getVariableValues().get("line"));
        assertEquals("3", match.getVariableValues().get("item"));
    }

    @Test
    void theLocatorOfAnOrderedEngineIsOrderedByItsEngine() {
        // the ordered engine prefers more variables with a regular expression
        @Nullable Object[] target = new Object[1];
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.group(g -> g.templates(OrderedColonRouteTemplateEngine.INSTANCE).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).locate("/o/:id", (request, variables) -> {
                target[0] = "plain";
                return "plain";
            }, t -> items));
            routes.group(g -> g.templates(OrderedColonRouteTemplateEngine.INSTANCE).selectedBy(OrderedColonRouteTemplateEngine.INSTANCE).locate("/o/:id([0-9]+)", (request, variables) -> {
                target[0] = "pattern";
                return "pattern";
            }, t -> items));
        });
        assertNotNull(router.findClosest(HttpRequest.GET("/o/1/items/2")));
        assertEquals("pattern", target[0]);
    }

    /**
     * A template of the colon language that finds where a match of it ends in one pass: after as
     * many segments as it has.
     */
    private record OnePassPrefix(RouteTemplate delegate, AtomicInteger matches, AtomicInteger prefixMatches) implements RouteTemplate {
        @Override
        public String expression() {
            return delegate.expression();
        }

        @Override
        public List<RouteTemplateVariable> variables() {
            return delegate.variables();
        }

        @Override
        public @Nullable RouteCaptures match(String rawPath) {
            matches.incrementAndGet();
            return delegate.match(rawPath);
        }

        @Override
        public @Nullable RouteCaptures matchPrefix(String rawPath) {
            prefixMatches.incrementAndGet();
            int segments = 0;
            for (String part : delegate.expression().split("/")) {
                if (!part.isEmpty()) {
                    segments++;
                }
            }
            int end = 0;
            for (int i = 0; i < segments; i++) {
                if (end >= rawPath.length()) {
                    return null;
                }
                int next = rawPath.indexOf('/', end + 1);
                end = next < 0 ? rawPath.length() : next;
            }
            String prefix = rawPath.substring(0, end);
            RouteCaptures captures = match(prefix);
            return captures == null ? null : RouteCaptures.of(prefix, captures.variables(), captures.values());
        }

        @Override
        public RouteTemplate mount(String prefix) {
            return new OnePassPrefix(delegate.mount(prefix), matches, prefixMatches);
        }

        @Override
        public String requiredPrefix() {
            return delegate.requiredPrefix();
        }

        @Override
        public int rawLength() {
            return delegate.rawLength();
        }
    }
}
