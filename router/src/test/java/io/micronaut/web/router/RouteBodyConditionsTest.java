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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.filter.FilteredRouter;
import io.micronaut.web.router.filter.RouteMatchFilter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.micronaut.web.router.builder.RouteCondition.all;
import static io.micronaut.web.router.builder.RouteCondition.any;
import static io.micronaut.web.router.builder.RouteCondition.body;
import static io.micronaut.web.router.builder.RouteCondition.bodyBytes;
import static io.micronaut.web.router.builder.RouteCondition.bodyText;
import static io.micronaut.web.router.builder.RouteCondition.header;
import static io.micronaut.web.router.builder.RouteCondition.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The router side of the body conditions of handler routes, {@link RouteCondition.Body}: the
 * routes a request matches without its body, ranked, pending on the body when a route of the
 * first rank has body conditions.
 */
class RouteBodyConditionsTest {

    private static final RouteCondition.Body<String> EXPRESS = bodyText(text -> text.contains("express"));
    private static final RouteCondition.Body<String> BULK = bodyText(text -> text.contains("bulk"));

    @Test
    void aRouterWithoutBodyConditionsMatchesAsBefore() {
        Router router = router(routes -> {
            routes.POST("/orders", handler("orders"));
            routes.GET("/orders/{id}", handler("order"));
        });

        assertFalse(router.hasBodyConditions());
        MutableHttpRequest<?> request = HttpRequest.POST("/orders", "{}");
        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        UriRouteMatch<Object, Object> match = Resolutions.resolve(router, request, pending::add);
        assertTrue(pending.isEmpty());
        assertEquals("orders", target(match));
        assertSame(router.findClosest(request).getRouteInfo(), match.getRouteInfo());
        assertNull(Resolutions.resolve(router, HttpRequest.POST("/missing", "{}"), pending::add));
        assertTrue(pending.isEmpty());
    }

    @Test
    void theResolutionIsAMatchNoRouteOrPendingOnTheBody() {
        Router router = router(routes -> {
            routes.POST("/orders").where(EXPRESS).handle(handler("express"));
            routes.POST("/payments", handler("payments"));
        });

        assertEquals("payments", target((UriRouteMatch<Object, Object>) assertInstanceOf(RouteResolution.Matched.class, router.resolve(HttpRequest.POST("/payments", "x"))).match()));
        assertInstanceOf(RouteResolution.NotFound.class, router.resolve(HttpRequest.POST("/missing", "x")));
        RouteResolution.Pending<?, ?> pending = assertInstanceOf(RouteResolution.Pending.class, router.resolve(HttpRequest.POST("/orders", "express")));
        assertEquals(1, pending.pending().getRanks().size());
        assertNull(RouteResolution.notFound().matchOrNull());
    }

    @Test
    void aRequestWhoseRoutesHaveNoBodyConditionsIsMatchedWithoutItsBody() {
        Router router = router(routes -> {
            routes.POST("/orders").where(EXPRESS).handle(handler("express"));
            routes.POST("/payments", handler("payments"));
        });

        assertTrue(router.hasBodyConditions());
        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        UriRouteMatch<Object, Object> match = Resolutions.resolve(router, HttpRequest.POST("/payments", "{}"), pending::add);
        assertEquals("payments", target(match));
        assertTrue(pending.isEmpty());
        assertNull(Resolutions.resolve(router, HttpRequest.POST("/missing", "{}"), pending::add));
        assertTrue(pending.isEmpty());
    }

    @Test
    void theRoutesAreRankedBySpecificityThenOrderUpToTheFirstRouteWithoutBodyConditions() {
        Router router = router(routes -> {
            routes.POST("/orders/{kind}", handler("by kind"));
            routes.POST("/orders/special", handler("fallback"));
            routes.POST("/orders/special").where(BULK).order(-1).handle(handler("bulk"));
            routes.POST("/orders/special").where(EXPRESS).order(-2).handle(handler("express"));
        });

        PendingRouteMatch<Object, Object> pending = pending(router, HttpRequest.POST("/orders/special", "express"));
        assertEquals(List.of(List.of("express"), List.of("bulk"), List.of("fallback")), names(pending));
    }

    @Test
    void aLessSpecificRouteIsTriedAfterTheMoreSpecificRoutesWithBodyConditions() {
        Router router = router(routes -> {
            routes.POST("/items/special").where(EXPRESS).handle(handler("special"));
            routes.POST("/items/{name}", handler("by name"));
        });

        PendingRouteMatch<Object, Object> pending = pending(router, HttpRequest.POST("/items/special", "x"));
        assertEquals(List.of(List.of("special"), List.of("by name")), names(pending));
        // another path: the body does not decide
        assertEquals("by name", target(Resolutions.resolve(router, HttpRequest.POST("/items/lamp", "x"), ignored -> { })));
    }

    @Test
    void aFirstRankWithoutBodyConditionsDecidesAsBefore() {
        Router router = router(routes -> {
            routes.POST("/items/special", handler("special"));
            routes.POST("/items/{name}").where(EXPRESS).handle(handler("by name"));
        });

        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        assertEquals("special", target(Resolutions.resolve(router, HttpRequest.POST("/items/special", "x"), pending::add)));
        assertTrue(pending.isEmpty());

        Router ambiguous = router(routes -> {
            routes.POST("/items", handler("a"));
            routes.POST("/items", handler("b"));
            routes.POST("/items").where(EXPRESS).order(1).handle(handler("c"));
        });
        assertThrows(DuplicateRouteException.class, () -> Resolutions.resolve(ambiguous, HttpRequest.POST("/items", "x"), ignored -> { }));
    }

    @Test
    void routesOfTheSameRankThatBothMatchAreAmbiguous() {
        Router router = router(routes -> {
            routes.POST("/orders").where(EXPRESS).handle(handler("express"));
            routes.POST("/orders").where(BULK).handle(handler("bulk"));
        });

        MutableHttpRequest<?> request = HttpRequest.POST("/orders", "x");
        PendingRouteMatch<Object, Object> pending = pending(router, request);
        List<UriRouteMatch<Object, Object>> rank = pending.getRanks().get(0);
        assertEquals(2, rank.size());
        assertEquals("express", target(Resolutions.choose(pending, request, List.of(rank.get(0)))));
        assertThrows(DuplicateRouteException.class, () -> Resolutions.choose(pending, request, rank));
        assertNull(Resolutions.choose(pending, request, List.of()));
    }

    @Test
    void theBodyConditionsOfTheGroupsAndOfTheRouteAreAllMetOuterGroupFirstAndLast() {
        RouteCondition.Body<String> route = bodyText(text -> true);
        RouteCondition.Body<byte[]> inner = bodyBytes(bytes -> true);
        RouteCondition.Body<Integer> outer = body(Integer.class, number -> true);
        Router router = router(routes -> routes.path("/shops", shop -> {
            shop.path("/orders", orders -> {
                orders.POST("/").where(route.and(header("X-Shop"))).handle(handler("order"));
                orders.where(inner);
            });
            // declared after the routes of the group
            shop.where(outer);
        }));

        PendingRouteMatch<Object, Object> pending = pending(router, HttpRequest.POST("/shops/orders", "x").header("X-Shop", "1"));
        UriRouteInfo<Object, Object> info = pending.getRanks().get(0).get(0).getRouteInfo();
        assertEquals(List.of(outer, inner, route), info.getBodyConditions());
        assertEquals(List.of(header("X-Shop"), outer, inner, route), info.getConditions(), "the body conditions are evaluated last");
        // the other conditions are met without the body
        assertNull(Resolutions.resolve(router, HttpRequest.POST("/shops/orders", "x"), ignored -> {
            throw new AssertionError("pending");
        }));
    }

    @Test
    void aBodyConditionCannotBeAPartOfACombinationARequestMayNotMeet() {
        assertThrows(IllegalArgumentException.class, () -> any(EXPRESS, header("X-Express")));
        assertThrows(IllegalArgumentException.class, () -> header("X-Express").or(EXPRESS));
        assertThrows(IllegalArgumentException.class, () -> not(EXPRESS));
        assertThrows(IllegalArgumentException.class, EXPRESS::negate);
        assertThrows(IllegalArgumentException.class, () -> not(all(header("X-Express"), EXPRESS)));
        assertThrows(IllegalArgumentException.class, () -> any(header("X-A"), EXPRESS.and(BULK)));
        // a part every request meets
        assertEquals(List.of(EXPRESS, BULK), ((RouteCondition.AllOf) all(EXPRESS, BULK)).conditions());
    }

    @Test
    void aBodyConditionLimitIsNotNegative() {
        assertThrows(IllegalArgumentException.class, () -> EXPRESS.maxBytes(-2));
        assertEquals(RouteCondition.Body.SERVER_LIMIT, EXPRESS.maxBytes(10).maxBytes(RouteCondition.Body.SERVER_LIMIT).maxBytes());
        assertEquals(10, EXPRESS.maxBytes(10).effectiveMaxBytes(100));
        assertEquals(100, EXPRESS.maxBytes(1000).effectiveMaxBytes(100), "a limit cannot raise the limit of the server");
        assertEquals(100, EXPRESS.effectiveMaxBytes(100));
    }

    @Test
    void theImplicitHeadRouteHasTheBodyConditionsOfItsGetRoute() {
        Router router = router(routes -> routes.GET("/reports").where(EXPRESS).handle(handler("report")));

        PendingRouteMatch<Object, Object> pending = pending(router, HttpRequest.HEAD("/reports"));
        UriRouteMatch<Object, Object> head = pending.getRanks().get(0).get(0);
        assertEquals(HttpMethod.HEAD, head.getRouteInfo().getHttpMethod());
        assertTrue(head.getRouteInfo().isImplicitHead());
        assertEquals(List.of(EXPRESS), head.getRouteInfo().getBodyConditions());
    }

    @Test
    void theMethodsAllowedForA405AreFoundWithoutTheBody() {
        Router router = router(routes -> routes.POST("/orders").where(EXPRESS).handle(handler("express")));

        assertNull(Resolutions.resolve(router, HttpRequest.PUT("/orders", "x"), pending -> {
            throw new AssertionError("pending");
        }));
        assertEquals(1, router.findAny(HttpRequest.PUT("/orders", "x")).size());
    }

    @Test
    void theLimitIsTheLargestOfTheRoutesWithinTheLimitOfTheServer() {
        Router router = router(routes -> {
            routes.POST("/orders").where(EXPRESS.maxBytes(10)).order(-2).handle(handler("small"));
            routes.POST("/orders").where(BULK.maxBytes(100)).order(-1).handle(handler("large"));
            routes.POST("/orders").where(BULK).handle(handler("server limit"));
        });

        PendingRouteMatch<Object, Object> pending = pending(router, HttpRequest.POST("/orders", "x"));
        assertEquals(50, pending.getMaxBytes(50));
        assertEquals(1000, pending.getMaxBytes(1000));

        Router limited = router(routes -> {
            routes.POST("/orders").where(EXPRESS.maxBytes(10)).order(-1).handle(handler("small"));
            routes.POST("/orders").where(BULK.maxBytes(100)).handle(handler("large"));
        });
        assertEquals(100, pending(limited, HttpRequest.POST("/orders", "x")).getMaxBytes(1000));
    }

    @Test
    void aFilteredRouterRanksTheRoutesItKeeps() {
        Router router = new FilteredRouter(router(routes -> {
            routes.POST("/orders").where(BULK).order(-2).handle(handler("hidden"));
            routes.POST("/orders").where(EXPRESS).order(-1).handle(handler("express"));
            routes.POST("/orders", handler("fallback"));
        }), new RouteMatchFilter() {
            @Override
            @SuppressWarnings("unchecked")
            public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request) {
                return match -> !"hidden".equals(target((UriRouteMatch<Object, Object>) (UriRouteMatch<?, ?>) match));
            }
        });

        assertTrue(router.hasBodyConditions());
        PendingRouteMatch<Object, Object> pending = pending(router, HttpRequest.POST("/orders", "x"));
        assertEquals(List.of(List.of("express"), List.of("fallback")), names(pending));
    }

    private static PendingRouteMatch<Object, Object> pending(Router router, MutableHttpRequest<?> request) {
        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        assertNull(Resolutions.resolve(router, request, pending::add));
        assertEquals(1, pending.size());
        return pending.get(0);
    }

    private static List<List<String>> names(PendingRouteMatch<Object, Object> pending) {
        List<List<String>> names = new ArrayList<>();
        for (List<UriRouteMatch<Object, Object>> rank : pending.getRanks()) {
            names.add(rank.stream().map(RouteBodyConditionsTest::target).toList());
        }
        return names;
    }

    private static String target(UriRouteMatch<Object, Object> match) {
        assertNotNull(match);
        return ((Named) ((HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name();
    }

    private static RequestHandler handler(String name) {
        return new Named(name);
    }

    private record Named(String name) implements RequestHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok(name);
        }
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
