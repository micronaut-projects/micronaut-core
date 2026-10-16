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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default pending lookup of a {@link Router} that is not a {@link DefaultRouter}, see
 * {@link Router#findClosest(HttpRequest, Predicate, Consumer)}: it ranks the routes with body
 * conditions, resolves a chosen route locator, and resolves the routes of the targets of route
 * locators otherwise.
 */
class RouterDefaultBodyConditionsTest {

    private static final RequestCondition.Body<String> VIP = RequestCondition.bodyText(text -> text.startsWith("vip"));

    @Test
    void aRouterWithoutBodyConditionsFindsTheClosestRoute() {
        Router router = new DelegatingRouter(router(routes -> routes.POST("/orders", (request, pathVariables) -> HttpResponse.ok())));

        assertNotNull(Resolutions.resolve(router, HttpRequest.POST("/orders", "x"), ignored -> fail()));
        assertNotNull(Resolutions.resolve(router, HttpRequest.POST("/orders", "x"), match -> true, ignored -> fail()));
        assertNull(Resolutions.resolve(router, HttpRequest.POST("/orders", "x"), match -> false, ignored -> fail()));
    }

    @Test
    void theRoutesWithBodyConditionsArePending() {
        Router router = new DelegatingRouter(router(routes -> {
            routes.POST("/orders").where(VIP).order(-1).handle((request, pathVariables) -> HttpResponse.ok("vip"));
            routes.POST("/orders", (request, pathVariables) -> HttpResponse.ok("standard"));
        }));

        MutableHttpRequest<?> request = HttpRequest.POST("/orders", "vip");
        List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
        assertNull(Resolutions.resolve(router, request, pending::add));
        assertEquals(2, pending.getFirst().getRanks().size());
        assertNotNull(Resolutions.choose(pending.getFirst(), request, pending.getFirst().getRanks().getFirst()));

        // the filter rejects the route with body conditions: the body does not decide
        assertNotNull(Resolutions.resolve(router, request, match -> match.getRouteInfo().getBodyConditions().isEmpty(), ignored -> fail()));
    }

    @Test
    void aRouteLocatorChosenByTheBodyLocatesItsTarget() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/items", (request, pathVariables) -> HttpResponse.ok("item")));
        Router router = new DelegatingRouter(router(routes -> routes.path("/vip", vip -> {
            vip.where(VIP);
            vip.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items);
        })));

        MutableHttpRequest<?> request = HttpRequest.POST("/vip/orders/1/items", "vip");
        for (Predicate<UriRouteMatch<Object, Object>> filter : List.<Predicate<UriRouteMatch<Object, Object>>>of(match -> true, match -> match.getRouteInfo().getHttpMethod() == HttpMethod.POST)) {
            List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
            assertNull(Resolutions.resolve(router, request, filter, pending::add));
            UriRouteMatch<Object, Object> chosen = Resolutions.choose(pending.getFirst(), request, pending.getFirst().getRanks().getFirst());
            assertNotNull(chosen);
            assertInstanceOf(RouteLocator.LocatedUriMatchInfo.class, ((DefaultUriRouteMatch<?, ?>) chosen).matchInfo());
        }
    }

    @Test
    void theRoutesOfALocatedTargetWithBodyConditionsArePending() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> {
            located.POST("/items").where(VIP).order(-1).handle((request, pathVariables) -> HttpResponse.ok("vip"));
            located.POST("/items", (request, pathVariables) -> HttpResponse.ok("standard"));
        });
        Router base = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items));
        MutableHttpRequest<?> request = HttpRequest.POST("/orders/1/items", "vip");

        // the default lookup and a filtered router, which resolves the routes of the target first
        for (Router router : List.of(new DelegatingRouter(base), new io.micronaut.web.router.filter.FilteredRouter(base, new io.micronaut.web.router.filter.RouteMatchFilter() {
            @Override
            public <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> r) {
                return match -> true;
            }
        }))) {
            List<PendingRouteMatch<Object, Object>> pending = new ArrayList<>();
            assertNull(Resolutions.resolve(router, request, match -> true, pending::add));
            assertEquals(1, pending.size());
            UriRouteMatch<Object, Object> chosen = Resolutions.choose(pending.getFirst(), request, pending.getFirst().getRanks().getFirst());
            assertNotNull(chosen);
            assertEquals(List.of(VIP), chosen.getRouteInfo().getBodyConditions());
        }
    }

    @Test
    void aLocatedTargetWithoutBodyConditionsIsResolved() {
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.POST("/items", (request, pathVariables) -> HttpResponse.ok("item")));
        Router router = new DelegatingRouter(router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> items)));

        UriRouteMatch<Object, Object> match = Resolutions.resolve(router, HttpRequest.POST("/orders/1/items", "x"), ignored -> fail());
        assertNotNull(match);
        assertInstanceOf(RouteLocator.LocatedUriMatchInfo.class, ((DefaultUriRouteMatch<?, ?>) match).matchInfo());
        assertTrue(router.hasBodyConditions(), "a route locator may resolve to routes with body conditions");
    }

    private static void fail() {
        throw new AssertionError("pending");
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    /**
     * A router that is not a {@link DefaultRouter}: it takes the default pending lookup.
     */
    record DelegatingRouter(Router router) implements Router {

        @Override
        public boolean hasBodyConditions() {
            return router.hasBodyConditions();
        }

        @Override
        public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri, @Nullable HttpRequest<?> context) {
            return router.findAny(uri, context);
        }

        @Override
        public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
            return router.findAny(request);
        }

        @Override
        public Set<Integer> getExposedPorts() {
            return router.getExposedPorts();
        }

        @Override
        public void applyDefaultPorts(List<Integer> ports) {
            router.applyDefaultPorts(ports);
        }

        @Override
        public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
            return router.find(httpMethod, uri, context);
        }

        @Override
        public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
            return router.find(request, uri);
        }

        @Override
        public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
            return router.findAllClosest(request);
        }

        @Override
        public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, Predicate<UriRouteMatch<T, R>> filter) {
            return router.findAllClosest(request, filter);
        }

        @Override
        public Stream<UriRouteInfo<?, ?>> uriRoutes() {
            return router.uriRoutes();
        }

        @Override
        public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
            return router.route(httpMethod, uri);
        }

        @Override
        public <R> Optional<RouteMatch<R>> route(HttpStatus status) {
            return router.route(status);
        }

        @Override
        public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, HttpStatus status) {
            return router.route(originatingClass, status);
        }

        @Override
        public <R> Optional<RouteMatch<R>> route(Throwable error) {
            return router.route(error);
        }

        @Override
        public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, Throwable error) {
            return router.route(originatingClass, error);
        }

        @Override
        public List<GenericHttpFilter> findFilters(HttpRequest<?> request) {
            return router.findFilters(request);
        }

        @Override
        public <R> Optional<RouteMatch<R>> findErrorRoute(Class<?> originatingClass, Throwable error, HttpRequest<?> request) {
            return router.findErrorRoute(originatingClass, error, request);
        }

        @Override
        public <R> Optional<RouteMatch<R>> findErrorRoute(Throwable error, HttpRequest<?> request) {
            return router.findErrorRoute(error, request);
        }

        @Override
        public <R> Optional<RouteMatch<R>> findStatusRoute(Class<?> originatingClass, HttpStatus status, HttpRequest<?> request) {
            return router.findStatusRoute(originatingClass, status, request);
        }

        @Override
        public <R> Optional<RouteMatch<R>> findStatusRoute(HttpStatus status, HttpRequest<?> request) {
            return router.findStatusRoute(status, request);
        }
    }
}
