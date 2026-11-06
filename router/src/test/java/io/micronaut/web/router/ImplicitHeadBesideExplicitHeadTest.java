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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An explicit {@code HEAD} route of the same URI does not hide the implicit {@code HEAD} route of
 * a functional {@code GET} route: when they differ by port, media type or condition, each answers
 * its own requests, and only when nothing tells them apart does the explicit one win.
 */
class ImplicitHeadBesideExplicitHeadTest {

    private static final int GET_PORT = 8081;
    private static final int HEAD_PORT = 8082;
    private static final Predicate<HttpRequest<?>> CSV = request -> "csv".equals(request.getHeaders().get("X-Export"));

    @Test
    void anExplicitHeadRouteOnAnotherPortKeepsTheImplicitOne() {
        Router router = router(routes -> {
            routes.GET("/x").port(GET_PORT).handle(handler("get"));
            routes.route(HttpMethod.HEAD, "/x").port(HEAD_PORT).handle(handler("head"));
        });

        UriRouteMatch<Object, Object> implicit = router.findClosest(RoutePortTest.onPort(HttpRequest.HEAD("/x"), GET_PORT));
        assertNotNull(implicit);
        assertTrue(implicit.getRouteInfo().isImplicitHead());
        assertEquals("get", name(implicit));
        UriRouteMatch<Object, Object> explicit = router.findClosest(RoutePortTest.onPort(HttpRequest.HEAD("/x"), HEAD_PORT));
        assertNotNull(explicit);
        assertFalse(explicit.getRouteInfo().isImplicitHead());
        assertEquals("head", name(explicit));
    }

    @Test
    void anExplicitHeadRouteProducingAnotherMediaTypeKeepsTheImplicitOne() {
        Router router = router(routes -> {
            routes.GET("/x").produces(MediaType.APPLICATION_JSON_TYPE).handle(handler("get"));
            routes.route(HttpMethod.HEAD, "/x").produces(MediaType.TEXT_PLAIN_TYPE).handle(handler("head"));
        });

        UriRouteMatch<Object, Object> implicit = router.findClosest(HttpRequest.HEAD("/x").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON));
        assertNotNull(implicit);
        assertTrue(implicit.getRouteInfo().isImplicitHead());
        assertEquals("get", name(implicit));
        UriRouteMatch<Object, Object> explicit = router.findClosest(HttpRequest.HEAD("/x").header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN));
        assertNotNull(explicit);
        assertFalse(explicit.getRouteInfo().isImplicitHead());
        assertEquals("head", name(explicit));
    }

    @Test
    void anExplicitHeadRouteWithAConditionKeepsTheImplicitOne() {
        Router router = router(routes -> {
            routes.GET("/x", handler("get"));
            routes.route(HttpMethod.HEAD, "/x").where(CSV).handle(handler("head"));
        });

        UriRouteMatch<Object, Object> implicit = router.findClosest(HttpRequest.HEAD("/x"));
        assertNotNull(implicit);
        assertTrue(implicit.getRouteInfo().isImplicitHead());
        assertEquals("get", name(implicit));
        UriRouteMatch<Object, Object> explicit = router.findClosest(HttpRequest.HEAD("/x").header("X-Export", "csv"));
        assertNotNull(explicit);
        assertFalse(explicit.getRouteInfo().isImplicitHead());
        assertEquals("head", name(explicit));
    }

    @Test
    void anExplicitHeadRouteWithTheSameSettingsWins() {
        Router router = router(routes -> {
            routes.GET("/x", handler("get"));
            routes.route(HttpMethod.HEAD, "/x").handle(handler("head"));
        });

        // the implicit route still exists
        assertTrue(router.uriRoutes().anyMatch(route -> route.getHttpMethod() == HttpMethod.HEAD && route.isImplicitHead()));
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.HEAD("/x"));
        assertNotNull(match);
        assertFalse(match.getRouteInfo().isImplicitHead());
        assertEquals("head", name(match));
    }

    @Test
    void aRouteDeclaredForGetAndHeadHasNoImplicitHeadRoute() {
        Router router = router(routes -> routes.route(Set.of(HttpMethod.GET, HttpMethod.HEAD), "/x").handle(handler("both")));

        List<UriRouteInfo<?, ?>> heads = router.uriRoutes().filter(route -> route.getHttpMethod() == HttpMethod.HEAD).toList();
        assertEquals(1, heads.size());
        assertFalse(heads.getFirst().isImplicitHead());
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.HEAD("/x"));
        assertNotNull(match);
        assertEquals("both", name(match));
    }

    private static String name(UriRouteMatch<Object, Object> match) {
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
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
