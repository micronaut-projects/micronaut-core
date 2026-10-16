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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.filter.RouteMatchFilter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of a located target with body conditions are pending on the body like the routes
 * of the router: the chosen route is a match of the target, under the prefix of its locator.
 */
class LocatedRouteBodyConditionsTest {

    private static final RouteCondition.Body<String> EXPRESS = RouteCondition.bodyText(text -> text.contains("express"));

    @Test
    void theRoutesOfALocatedTargetArePendingOnTheBody() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.POST("/items").where(EXPRESS).order(-1).handle((request, pathVariables) -> HttpResponse.ok("express"));
            located.POST("/items", (request, pathVariables) -> HttpResponse.ok("standard"));
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        assertTrue(router.hasBodyConditions(), "a located target may have routes with body conditions");
        MutableHttpRequest<?> request = HttpRequest.POST("/orders/1/items", "express");
        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        assertNull(router.findClosest(request, pending::add));
        assertEquals(1, pending.size());
        List<List<UriRouteMatch<Object, Object>>> ranks = pending.get(0).getRanks();
        assertEquals(2, ranks.size(), "the conditioned route, then the fallback");
        assertEquals(List.of(EXPRESS), ranks.get(0).get(0).getRouteInfo().getBodyConditions());

        UriRouteMatch<Object, Object> chosen = pending.get(0).choose(request, ranks.get(0));
        assertNotNull(chosen);
        // a match of the request: the target is bound to it
        assertInstanceOf(RouteLocator.LocatedUriMatchInfo.class, ((DefaultUriRouteMatch<?, ?>) chosen).matchInfo());
        assertEquals("order", ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) chosen).matchInfo()).target());
    }

    @Test
    void aLocatedTargetWithoutBodyConditionsIsMatchedWithoutTheBody() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));

        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        assertNotNull(router.findClosest(HttpRequest.POST("/orders/1/items", "x"), pending::add));
        assertTrue(pending.isEmpty());
    }

    @Test
    void aFilteredRouterLocatesTheTargetOfALocator() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/items", (request, pathVariables) -> HttpResponse.ok("item")));
        Router router = new FilteredRouter(router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items)),
            new RouteMatchFilter() {
                @Override
                public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
                    return match -> true;
                }
            });

        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.POST("/orders/1/items", "x"), pending::add);
        assertNotNull(match);
        assertTrue(pending.isEmpty());
        // the match of the located route, not of the locator
        assertInstanceOf(RouteLocator.LocatedUriMatchInfo.class, ((DefaultUriRouteMatch<?, ?>) match).matchInfo());
    }

    @Test
    void stackedFilteredRoutersLocateTheTargetOfALocator() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/items", (request, pathVariables) -> HttpResponse.ok("item")));
        RouteMatchFilter all = new RouteMatchFilter() {
            @Override
            public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
                return match -> true;
            }
        };
        Router router = new FilteredRouter(new FilteredRouter(router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items)), all), all);

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.POST("/orders/1/items", "x"), ignored -> { });
        assertNotNull(match);
        assertInstanceOf(RouteLocator.LocatedUriMatchInfo.class, ((DefaultUriRouteMatch<?, ?>) match).matchInfo());
    }

    @Test
    void aFilteredRouterChoosesAmongTheRoutesOfLocatorsOfTheSamePrefixWithoutBodyConditions() {
        LocatedRoutes<?> v1 = TestLocatedRoutes.of(located -> located.GET("/items").attribute("version", "1").handle((request, pathVariables) -> HttpResponse.ok("v1")));
        LocatedRoutes<?> v2 = TestLocatedRoutes.of(located -> located.GET("/items").attribute("version", "2").handle((request, pathVariables) -> HttpResponse.ok("v2")));
        RouteMatchFilter versions = new RouteMatchFilter() {
            @Override
            public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
                String version = request.getHeaders().get("X-Version");
                return match -> match.getRouteInfo().getAttribute("version", String.class).map(v -> v.equals(version)).orElse(false);
            }
        };
        Router base = router(routes -> {
            routes.locate("/orders/{id}", (request, pathVariables) -> "v1", target -> v1);
            routes.locate("/orders/{id}", (request, pathVariables) -> "v2", target -> v2);
        });
        // the filter applies to the routes of the targets, not to the locators, also through stacked filtered routers
        for (Router router : List.of(new FilteredRouter(base, versions), new FilteredRouter(new FilteredRouter(base, versions), versions))) {
            for (String version : List.of("1", "2")) {
            MutableHttpRequest<?> request = HttpRequest.GET("/orders/1/items").header("X-Version", version);
            UriRouteMatch<Object, Object> match = router.findClosest(request, ignored -> { });
            assertNotNull(match);
            assertEquals(version, match.getRouteInfo().getAttribute("version", String.class).orElseThrow());
            // as without the pending lookup
            assertEquals(version, router.findClosest(request).getRouteInfo().getAttribute("version", String.class).orElseThrow());
            }
        }
    }

    @Test
    void aFilteredRouterKeepsTheFallbackRoutesOfALocatedTargetForTheBody() {
        LocatedRoutes<?> review = TestLocatedRoutes.of(located -> {
            located.POST("/items/special").where(RouteCondition.bodyText("express"::equals)).handle((request, pathVariables) -> HttpResponse.ok("special"));
            located.POST("/items/{name}", (request, pathVariables) -> HttpResponse.ok("by name"));
        });
        Router base = router(routes -> routes.locate("/review", (request, pathVariables) -> "review", target -> review));
        RouteMatchFilter all = new RouteMatchFilter() {
            @Override
            public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
                return match -> true;
            }
        };
        for (Router router : List.of(base, new FilteredRouter(base, all), new RouterDefaultBodyConditionsTest.DelegatingRouter(base))) {
            List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
            assertNull(router.findClosest(HttpRequest.POST("/review/items/special", "ordinary"), pending::add), router.toString());
            assertEquals(2, pending.getFirst().getRanks().size(), "the literal route, then its fallback: " + router);
        }
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
