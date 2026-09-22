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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locator routes whose prefix is a template of another engine, and located tables with routes of
 * another engine.
 */
class RouteTemplateEngineLocatorTest {

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new RouteBuilder.UriNamingStrategy() { },
        ConversionService.SHARED, null);
    private final List<Map<String, Object>> located = new ArrayList<>();

    private final RouteTable items = tables.buildLocatedHttpRoutes(routes -> {
        routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/items/:item")), (request, variables) -> HttpResponse.ok());
        routes.GET("/", (request, variables) -> HttpResponse.ok());
        routes.GET("/{+rest}", (request, variables) -> HttpResponse.ok());
    });

    @Test
    void aPrefixOfAnotherEngineLocatesTheRestOfThePath() {
        for (String contextPath : new String[] {null, "/ctx"}) {
            String prefix = contextPath == null ? "" : contextPath;
            Router router = RouteTemplateEngineOrderTest.router(contextPath, routes ->
                routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> {
                    located.add(Map.of("id", variables.getString("id")));
                    return "order-" + variables.getString("id");
                }, target -> items));

            UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(prefix + "/orders/5/items/3"));
            assertNotNull(match, prefix);
            assertEquals(ColonRouteTemplateEngine.template("/items/:item"), match.getRouteInfo().getRouteTemplate(), prefix);
            assertEquals("5", match.getVariableValues().get("id"), prefix);
            assertEquals("3", match.getVariableValues().get("item"), prefix);
            assertEquals(Map.of("id", "5"), located.get(located.size() - 1), prefix);

            // the prefix alone, and with a trailing slash, locates the root of the table
            assertEquals(RouteTemplate.micronaut("/"), router.findClosest(HttpRequest.GET(prefix + "/orders/5")).getRouteInfo().getRouteTemplate(), prefix);
            assertEquals(RouteTemplate.micronaut("/"), router.findClosest(HttpRequest.GET(prefix + "/orders/5/")).getRouteInfo().getRouteTemplate(), prefix);
            // the rest of the path is matched by the table, with its own engines
            UriRouteMatch<Object, Object> rest = router.findClosest(HttpRequest.GET(prefix + "/orders/5/a/b"));
            assertEquals(RouteTemplate.micronaut("/{+rest}"), rest.getRouteInfo().getRouteTemplate(), prefix);
            assertEquals("a/b", rest.getVariableValues().get("rest"), prefix);
            // a path the prefix does not match is not located
            int before = located.size();
            assertNull(router.findClosest(HttpRequest.GET(prefix + "/orders")), prefix);
            assertNull(router.findClosest(HttpRequest.GET(prefix + "/other/5/items/3")), prefix);
            assertEquals(before, located.size(), prefix);
        }
    }

    @Test
    void theLocatorRoutesOfAnEngineAnswerEveryStandardMethod() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> items));
        List<HttpMethod> methods = router.uriRoutes()
            .filter(r -> r.getRouteTemplate().engineId().equals(ColonRouteTemplateEngine.ID))
            .map(UriRouteInfo::getHttpMethod)
            .sorted()
            .toList();
        List<HttpMethod> expected = new ArrayList<>(List.of(HttpMethod.values()));
        expected.remove(HttpMethod.CUSTOM);
        assertEquals(expected, methods);
    }

    @Test
    void aLiteralSegmentAfterTheLocatorIsNotPartOfThePrefix() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes -> {
            routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> items);
            // more literal text: selected over the locator
            routes.handle(RouteDeclaration.of(HttpMethod.GET, ColonRouteTemplateEngine.template("/orders/:id/summary")), (request, variables) -> HttpResponse.ok());
        });
        assertEquals(ColonRouteTemplateEngine.template("/orders/:id/summary"),
            router.findClosest(HttpRequest.GET("/orders/5/summary")).getRouteInfo().getRouteTemplate());
        assertEquals(ColonRouteTemplateEngine.template("/items/:item"),
            router.findClosest(HttpRequest.GET("/orders/5/items/1")).getRouteInfo().getRouteTemplate());
    }

    @Test
    void aMicronautTemplateIsTheSameAsTheStringPrefix() {
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(RouteTemplate.micronaut("/orders/{id}"), (request, variables) -> "order", target -> items));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/items/3"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals(RouteTemplate.micronaut("/orders/{id}/{+" + RouteLocator.REMAINDER + "}"),
            router.uriRoutes().filter(r -> r.getHttpMethod() == HttpMethod.GET && r.getRouteTemplate().expression().contains("+"))
                .findFirst().orElseThrow().getRouteTemplate());
    }

    @Test
    void aLocatedTableLocatesAgainWithAnotherEngine() {
        RouteTable orders = tables.buildLocatedHttpRoutes(routes ->
            routes.locate(ColonRouteTemplateEngine.template("/lines/:line"), (request, variables) -> "line", target -> items));
        Router router = RouteTemplateEngineOrderTest.router(null, routes ->
            routes.locate(ColonRouteTemplateEngine.template("/orders/:id"), (request, variables) -> "order", target -> orders));
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
            routes.locate(OrderedColonRouteTemplateEngine.template("/o/:id"), (request, variables) -> {
                target[0] = "plain";
                return "plain";
            }, t -> items);
            routes.locate(OrderedColonRouteTemplateEngine.template("/o/:id([0-9]+)"), (request, variables) -> {
                target[0] = "pattern";
                return "pattern";
            }, t -> items);
        });
        assertNotNull(router.findClosest(HttpRequest.GET("/o/1/items/2")));
        assertEquals("pattern", target[0]);
    }
}
