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
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The conditions of handler routes and of groups of handler routes: a route whose conditions a
 * request does not meet does not match it.
 */
class RouteWhereTest {

    private static final Predicate<HttpRequest<?>> CSV = request -> "csv".equals(request.getHeaders().get("X-Export"));

    @Test
    void aConditionSelectsAmongRoutesOfTheSameUriAndMethod() {
        Router router = router(routes -> {
            routes.GET("/reports/{id}", handler("csv")).where(CSV);
            routes.GET("/reports/{id}", handler("json")).where(CSV.negate());
        });

        assertEquals("csv", target(router, HttpRequest.GET("/reports/1").header("X-Export", "csv")));
        assertEquals("json", target(router, HttpRequest.GET("/reports/1")));
    }

    @Test
    void aRequestTheConditionRejectsIsAnsweredAsIfTheRouteDidNotExist() {
        Router router = router(routes -> routes.GET("/reports/{id}", handler("csv")).where(CSV));

        assertNull(router.findClosest(HttpRequest.GET("/reports/1")));
        // no 405 either: no route of another method matches
        assertTrue(router.findAny(HttpRequest.POST("/reports/1", "")).stream()
            .noneMatch(match -> match.getRouteInfo().getHttpMethod() == HttpMethod.GET));
        // the implicit HEAD route has the condition of its GET route
        assertNull(router.findClosest(HttpRequest.HEAD("/reports/1")));
        assertNotNull(router.findClosest(HttpRequest.HEAD("/reports/1").header("X-Export", "csv")));
    }

    @Test
    void theConditionsOfTheGroupsAndOfTheRouteMustAllBeMet() {
        List<String> evaluated = new ArrayList<>();
        Router router = router(routes -> routes.path("/beta", beta -> {
            beta.path("/inner", inner -> {
                inner.GET("/search", handler("search")).where(record(evaluated, "route", request -> request.getHeaders().contains("X-Q")));
                inner.where(record(evaluated, "inner", request -> request.getHeaders().contains("X-Inner")));
            });
            // declared after the routes of the group
            beta.where(record(evaluated, "outer", request -> request.getHeaders().contains("X-Beta")));
        }));

        assertEquals("search", target(router, HttpRequest.GET("/beta/inner/search").header("X-Q", "1").header("X-Beta", "1").header("X-Inner", "1")));
        assertEquals(List.of("outer", "inner", "route"), evaluated, "outer group first");
        assertNull(router.findClosest(HttpRequest.GET("/beta/inner/search").header("X-Q", "1").header("X-Inner", "1")));
        assertNull(router.findClosest(HttpRequest.GET("/beta/inner/search").header("X-Q", "1").header("X-Beta", "1")));
        assertNull(router.findClosest(HttpRequest.GET("/beta/inner/search").header("X-Beta", "1").header("X-Inner", "1")));
    }

    @Test
    void twoRoutesWhoseConditionsARequestBothMeetAreAmbiguous() {
        Router router = router(routes -> {
            routes.GET("/reports/{id}", handler("csv")).where(CSV);
            routes.GET("/reports/{id}", handler("any"));
        });

        assertEquals("any", target(router, HttpRequest.GET("/reports/1")));
        assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/reports/1").header("X-Export", "csv")));
    }

    @Test
    void aDeclaredRouteAndALocatorRouteHaveTheConditionsOfTheirGroups() {
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, "/declared/{id}");
        LocatedRoutes<?> items = TestLocatedRoutes.of(located -> located.GET("/items", handler("items")));
        Router router = router(routes -> routes.group(group -> {
            group.where(CSV);
            group.handle(declaration, handler("declared")).where(request -> request.getHeaders().contains("X-Q"));
            group.locate("/orders/{id}", (request, pathVariables) -> pathVariables.getLong("id"), target -> items);
        }));

        assertEquals("declared", target(router, HttpRequest.GET("/declared/1").header("X-Q", "1").header("X-Export", "csv")));
        assertNull(router.findClosest(HttpRequest.GET("/declared/1").header("X-Q", "1")));
        assertNull(router.findClosest(HttpRequest.GET("/declared/1").header("X-Export", "csv")));
        assertEquals("items", target(router, HttpRequest.GET("/orders/1/items").header("X-Export", "csv")));
        assertNull(router.findClosest(HttpRequest.GET("/orders/1/items")));
    }

    @Test
    void aRouteOfSeveralMethodsHasTheConditionForEachMethod() {
        Router router = router(routes -> routes.handle(java.util.Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/both", handler("both")).where(CSV));

        assertNull(router.findClosest(HttpRequest.PUT("/both", "")));
        assertNull(router.findClosest(HttpRequest.PATCH("/both", "")));
        assertEquals("both", target(router, HttpRequest.PUT("/both", "").header("X-Export", "csv")));
        assertEquals("both", target(router, HttpRequest.PATCH("/both", "").header("X-Export", "csv")));
    }

    private static Predicate<HttpRequest<?>> record(List<String> evaluated, String name, Predicate<HttpRequest<?>> condition) {
        return request -> {
            evaluated.add(name);
            return condition.test(request);
        };
    }

    private static String target(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return ((Named) ((io.micronaut.web.router.builder.HandlerMethod<?>) match.getRouteInfo().getTargetMethod()).getTarget()).name();
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
