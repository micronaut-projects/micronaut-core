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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteGroup;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Groups of handler routes: {@link HttpRouteBuilder#group} and {@link HttpRouteBuilder#path}
 * prefix the URI templates of their routes and apply their filters to every route declared in
 * them, wherever the filter is declared, outer groups first.
 */
class RouteGroupsTest {

    @Test
    void prefixesAreJoinedLikeControllerUrisAndNestedPrefixesAddUp() {
        Router router = router(routes -> routes.path("/api/", api -> {
            api.GET("/orders", RouteGroupsTest::ok);
            api.GET("items", RouteGroupsTest::ok);
            api.GET("/", RouteGroupsTest::ok);
            api.GET("/search{?q}", RouteGroupsTest::ok);
            api.path("tenants/{tenant}", tenant -> tenant.GET("/users/{id}", RouteGroupsTest::ok));
            api.path("/", root -> root.GET("/root", RouteGroupsTest::ok));
            api.group(group -> group.GET("/grouped", RouteGroupsTest::ok));
        }));

        List<String> templates = router.uriRoutes()
            .filter(route -> route.getHttpMethod() == HttpMethod.GET)
            .map(route -> route.getUriMatchTemplate().toString())
            .sorted()
            .toList();
        assertEquals(List.of("/api", "/api/grouped", "/api/items", "/api/orders", "/api/root", "/api/search{?q}",
            "/api/tenants/{tenant}/users/{id}"), templates);

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/api/tenants/acme/users/5"));
        assertNotNull(match);
        assertEquals("acme", match.getVariableValues().get("tenant"));
        assertEquals("5", match.getVariableValues().get("id"));
    }

    @Test
    void aPrefixIsAPath() {
        for (String prefix : List.of("/api?x=1", "/api#top", "/api{?q}", "/api{&q}", "/api{#f}")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> router(routes -> routes.path(prefix, api -> api.GET("/orders", RouteGroupsTest::ok))));
            assertTrue(error.getMessage().contains(prefix), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
            () -> router(routes -> routes.path("/api", api -> api.GET("?q", RouteGroupsTest::ok))));
    }

    @Test
    void aGroupFilterCoversEveryRouteOfTheGroupWhereverItIsDeclared() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> {
            routes.GET("/outside", RouteGroupsTest::ok);
            routes.path("/api", api -> {
                api.GET("/before-the-filter", RouteGroupsTest::ok);
                api.before(request -> record(trace, "api"));
                api.GET("/after-the-filter", RouteGroupsTest::ok);
                api.path("/nested", nested -> nested.GET("/route", RouteGroupsTest::ok));
                api.POST("/declared-last", RouteGroupsTest::ok);
            });
            routes.GET("/declared-after-the-group", RouteGroupsTest::ok);
        });

        for (String path : List.of("/api/before-the-filter", "/api/after-the-filter", "/api/nested/route")) {
            trace.clear();
            run(router, HttpRequest.GET(path), trace);
            assertEquals(List.of("api", "handler"), trace, path);
        }
        trace.clear();
        run(router, HttpRequest.POST("/api/declared-last", ""), trace);
        assertEquals(List.of("api", "handler"), trace);
        // the implicit HEAD route of a GET route of the group
        trace.clear();
        run(router, HttpRequest.HEAD("/api/after-the-filter"), trace);
        assertEquals(List.of("api", "handler"), trace);

        for (String path : List.of("/outside", "/declared-after-the-group")) {
            trace.clear();
            run(router, HttpRequest.GET(path), trace);
            assertEquals(List.of("handler"), trace, path);
        }
    }

    @Test
    void filtersRunFromTheOuterGroupToTheRouteAndResponseFiltersTheOtherWay() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.group(outer -> {
            outer.path("/api", inner -> {
                inner.GET("/orders", RouteGroupsTest::ok)
                    .before(request -> record(trace, "route-before1"))
                    .before(request -> record(trace, "route-before2"))
                    .after((request, response) -> trace.add("route-after1"))
                    .after((request, response) -> trace.add("route-after2"));
                inner.after((request, response) -> trace.add("inner-after1"));
                inner.before(request -> record(trace, "inner-before1"));
                inner.after((request, response) -> trace.add("inner-after2"));
                inner.before(request -> record(trace, "inner-before2"));
            });
            outer.before(request -> record(trace, "outer-before1"));
            outer.after((request, response) -> trace.add("outer-after1"));
            outer.before(request -> record(trace, "outer-before2"));
            outer.after((request, response) -> trace.add("outer-after2"));
        }));

        run(router, HttpRequest.GET("/api/orders"), trace);
        assertEquals(List.of(
            "outer-before1", "outer-before2", "inner-before1", "inner-before2", "route-before1", "route-before2",
            "handler",
            "route-after1", "route-after2", "inner-after1", "inner-after2", "outer-after1", "outer-after2"
        ), trace);
    }

    @Test
    void theResponseFiltersOfAGroupFilterTheResponseARequestFilterAnsweredWith() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.path("/api", api -> {
            api.after((request, response) -> trace.add("api-after " + response.code()));
            api.GET("/route-rejects", RouteGroupsTest::ok)
                .beforeReplacing(request -> HttpResponse.status(HttpStatus.FORBIDDEN))
                .after((request, response) -> trace.add("route-after " + response.code()));
            api.path("/inner", inner -> {
                inner.beforeReplacing(request -> HttpResponse.status(HttpStatus.UNAUTHORIZED));
                inner.after((request, response) -> trace.add("inner-after " + response.code()));
                inner.GET("/group-rejects", RouteGroupsTest::ok)
                    .after((request, response) -> trace.add("route-after " + response.code()));
            });
        }));

        assertEquals(HttpStatus.FORBIDDEN, run(router, HttpRequest.GET("/api/route-rejects"), trace).getStatus());
        assertEquals(List.of("route-after 403", "api-after 403"), trace);
        trace.clear();
        // the filters of the route run inside those of its group: the route did not run
        assertEquals(HttpStatus.UNAUTHORIZED, run(router, HttpRequest.GET("/api/inner/group-rejects"), trace).getStatus());
        assertEquals(List.of("inner-after 401", "api-after 401"), trace);
    }

    @Test
    void everyFilterVariantCompilesAndAppliesInAGroup() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.group(group -> {
            // the lambda arity selects the variant, as it does on a route
            HttpRouteGroup same = group
                .before(request -> record(trace, "before"))
                .before((request, propagatedContext) -> record(trace, "context-before"))
                .beforeAsync(request -> CompletableFuture.completedFuture(record(trace, "async-before")))
                .beforeAsync((request, propagatedContext) -> CompletableFuture.completedFuture(record(trace, "async-context-before")))
                .after((request, response) -> trace.add("after"))
                .after((request, response, propagatedContext) -> trace.add("context-after"))
                .afterAsync((request, response) -> CompletableFuture.completedFuture(trace.add("async-after")))
                .afterAsync((request, response, propagatedContext) -> CompletableFuture.completedFuture(trace.add("async-context-after")));
            assertTrue(same == group);
            HttpRouteSpec route = group.GET("/all", RouteGroupsTest::ok)
                .before(request -> { })
                .after((request, response) -> { })
                .produces(MediaType.TEXT_PLAIN_TYPE);
            assertNotNull(route);
        }));

        run(router, HttpRequest.GET("/all"), trace);
        assertEquals(List.of("before", "context-before", "async-before", "async-context-before", "handler",
            "after", "context-after", "async-after", "async-context-after"), trace);
    }

    @Test
    void everyReplacingRequestFilterVariantAppliesInAGroupAndCanAnswer() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> {
            routes.group(group -> {
                // the four families of request filters line up with the response filters
                group.beforeReplacing(request -> record(trace, "replacing"))
                    .beforeReplacing((request, propagatedContext) -> record(trace, "context-replacing"))
                    .beforeReplacingAsync(request -> CompletableFuture.completedFuture(record(trace, "async-replacing")))
                    .beforeReplacingAsync((request, propagatedContext) -> CompletableFuture.completedFuture(record(trace, "async-context-replacing")))
                    .before(request -> {
                        trace.add("in-place");
                        request.setAttribute("in-place", true);
                    })
                    .beforeAsync(request -> CompletableFuture.completedFuture(trace.add("async-in-place")));
                group.GET("/replacing", RouteGroupsTest::ok);
            });
            routes.group(group -> {
                group.beforeReplacingAsync(request -> CompletableFuture.completedFuture(HttpResponse.status(HttpStatus.FORBIDDEN)));
                group.before(request -> trace.add("not reached"));
                group.GET("/answered", RouteGroupsTest::ok);
            });
        });

        run(router, HttpRequest.GET("/replacing"), trace);
        assertEquals(List.of("replacing", "context-replacing", "async-replacing", "async-context-replacing", "in-place",
            "async-in-place", "handler"), trace);

        trace.clear();
        assertEquals(HttpStatus.FORBIDDEN, run(router, HttpRequest.GET("/answered"), trace).getStatus());
        assertEquals(List.of(), trace);
    }

    @Test
    void aGroupIsClosedWhenItsLambdaReturns() {
        AtomicReference<HttpRouteGroup> leaked = new AtomicReference<>();
        router(routes -> routes.path("/api", leaked::set));
        HttpRouteGroup group = leaked.get();

        assertThrows(IllegalStateException.class, () -> group.before(request -> { }));
        assertThrows(IllegalStateException.class, () -> group.afterAsync((request, response) -> CompletableFuture.completedFuture(null)));
        assertThrows(IllegalStateException.class, () -> group.GET("/late", RouteGroupsTest::ok));
        assertThrows(IllegalStateException.class, () -> group.group(nested -> { }));
    }

    @Test
    void aDeclaredRouteIsBoundInAGroupWithoutAPrefixOnly() {
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, "/declared/{id}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> router(routes ->
            routes.path("/api", api -> api.handle(declaration, RouteGroupsTest::ok))));
        assertTrue(error.getMessage().contains("GET /declared/{id}"), error.getMessage());
        assertTrue(error.getMessage().contains("/api"), error.getMessage());

        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.group(group -> {
            group.handle(declaration, RouteGroupsTest::ok);
            group.before(request -> record(trace, "group"));
        }));
        run(router, HttpRequest.GET("/declared/5"), trace);
        assertEquals(List.of("group", "handler"), trace);
    }

    @Test
    void errorAndStatusRoutesDeclaredInAGroupAreLocalToTheRoutesOfTheGroup() {
        // changed: the error and status routes of a group were global, they are local to the group now
        Router router = router(routes -> {
            routes.path("/api", api -> {
                api.before(request -> { });
                api.GET("/inside", RouteGroupsTest::ok);
                api.error(IllegalStateException.class, (request, error) -> HttpResponse.status(HttpStatus.CONFLICT));
                api.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound("none"));
            });
            routes.GET("/outside", RouteGroupsTest::ok);
        });

        HttpRequest<?> outside = HttpRequest.GET("/outside");
        assertTrue(router.findErrorRoute(new IllegalStateException(), outside).isEmpty(), "not global");
        assertTrue(router.findStatusRoute(HttpStatus.NOT_FOUND, HttpRequest.GET("/missing")).isEmpty(), "not global");
        UriRouteMatch<Object, Object> outsideMatch = router.findClosest(outside);
        assertNotNull(outsideMatch);
        RouteAttributes.setRouteMatch(outside, outsideMatch);
        assertNull(GroupErrorRoutes.findErrorRoute(outside, outsideMatch.getRouteInfo(), new IllegalStateException()));

        HttpRequest<?> inside = HttpRequest.GET("/api/inside");
        UriRouteMatch<Object, Object> insideMatch = router.findClosest(inside);
        assertNotNull(insideMatch);
        RouteAttributes.setRouteMatch(inside, insideMatch);
        RouteMatch<Object> error = GroupErrorRoutes.findErrorRoute(inside, insideMatch.getRouteInfo(), new IllegalStateException());
        assertNotNull(error);
        assertTrue(router.findFilters(inside, error).isEmpty(), "the group does not filter its error routes");
        assertNotNull(GroupErrorRoutes.findStatusRoute(inside, insideMatch.getRouteInfo(), HttpStatus.NOT_FOUND.getCode()));
    }

    @Test
    void theFiltersOfTheGroupOfALocatorRunBeforeTheFiltersOfTheLocatedRoute() {
        List<String> trace = new ArrayList<>();
        RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);
        RouteTable items = tables.buildLocatedHttpRoutes(order -> order.path("/items", itemsGroup -> {
            itemsGroup.before(request -> record(trace, "table-group"));
            itemsGroup.GET("/{item}", RouteGroupsTest::ok).before(request -> record(trace, "located-route"));
        }));
        RouteTable orders = tables.buildLocatedHttpRoutes(order -> order.group(inner -> {
            inner.before(request -> record(trace, "inner-locator-group"));
            inner.locate("/lines", (request, pathVariables) -> "lines", target -> items);
        }));
        Router router = router(routes -> routes.path("/shop", shop -> {
            shop.before(request -> record(trace, "shop"));
            shop.locate("/orders/{id}", (request, pathVariables) -> pathVariables.getLong("id"), target -> orders);
        }));

        HttpRequest<?> request = HttpRequest.GET("/shop/orders/5/lines/items/3");
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        assertEquals("3", match.getVariableValues().get("item"));
        run(router, request, match, trace);
        assertEquals(List.of("shop", "inner-locator-group", "table-group", "located-route", "handler"), trace);

        // not located: no route, no filters
        assertNull(router.findClosest(HttpRequest.GET("/shop/other")));
    }

    @Test
    void aGroupFilterDoesNotApplyToTheRoutesOfAnotherGroupOnTheSameBuilder() {
        List<String> trace = new ArrayList<>();
        RouteAssembly assembly = assembly();
        DefaultHttpRouteBuilder shared = new DefaultHttpRouteBuilder(assembly);
        // two HttpRoutes beans share the builder
        Consumer<HttpRouteBuilder> first = routes -> routes.group(all -> {
            all.before(request -> record(trace, "first"));
            all.GET("/first", RouteGroupsTest::ok);
        });
        Consumer<HttpRouteBuilder> second = routes -> routes.GET("/second", RouteGroupsTest::ok);
        first.accept(shared);
        second.accept(shared);
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        run(router, HttpRequest.GET("/first"), trace);
        assertEquals(List.of("first", "handler"), trace);
        trace.clear();
        run(router, HttpRequest.GET("/second"), trace);
        assertEquals(List.of("handler"), trace);
    }

    @Test
    void aRouteOfSeveralMethodsInAGroupHasTheFiltersOfTheGroup() {
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> routes.path("/api", api -> {
            api.handle(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/both", RouteGroupsTest::ok);
            api.before(request -> record(trace, "api"));
        }));
        run(router, HttpRequest.PUT("/api/both", ""), trace);
        run(router, HttpRequest.PATCH("/api/both", ""), trace);
        assertEquals(List.of("api", "handler", "api", "handler"), trace);
    }

    private static HttpResponse<?> run(Router router, HttpRequest<?> request, List<String> trace) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return run(router, request, match, trace);
    }

    private static HttpResponse<?> run(Router router, HttpRequest<?> request, RouteMatch<?> match, List<String> trace) {
        List<GenericHttpFilter> filters = router.findFilters(request, match);
        ExecutionFlow<HttpResponse<?>> flow = new FilterRunner(filters, (r, propagatedContext) -> {
            trace.add("handler");
            return ExecutionFlow.just(HttpResponse.ok());
        }).run(request);
        HttpResponse<?> response = flow.tryCompleteValue();
        assertNotNull(response, "the filters completed synchronously");
        return response;
    }

    private static HttpResponse<?> record(List<String> trace, String step) {
        trace.add(step);
        return null;
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, io.micronaut.web.router.builder.PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static RouteAssembly assembly() {
        return new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = assembly();
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
