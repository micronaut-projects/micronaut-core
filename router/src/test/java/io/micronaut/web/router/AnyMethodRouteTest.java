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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes of {@link HttpRouteBuilder#any(String, RequestHandler)}: every standard and custom
 * HTTP method, behind the routes of a specific method and the implicit {@code HEAD} routes.
 */
class AnyMethodRouteTest {

    @Test
    void anyMatchesEveryStandardAndCustomMethod() {
        Router router = router(routes -> routes.any("/things/{id}", handler("any")));

        for (HttpMethod method : HttpMethod.values()) {
            if (method != HttpMethod.CUSTOM) {
                assertEquals("any", target(router, HttpRequest.create(method, "/things/1")), method.name());
            }
        }
        assertEquals("any", target(router, HttpRequest.create(HttpMethod.CUSTOM, "/things/1", "PROPFIND")));
        assertEquals("any", target(router, HttpRequest.create(HttpMethod.CUSTOM, "/things/1", "LOCK")));
        assertNull(router.findClosest(HttpRequest.GET("/other")));
    }

    @Test
    void aRouteOfASpecificMethodOnTheSamePathWins() {
        Router router = router(routes -> {
            routes.any("/things/{id}", handler("any"));
            routes.GET("/things/{id}", handler("get"));
            routes.handle("PROPFIND", "/things/{id}", handler("propfind"));
        });

        assertEquals("get", target(router, HttpRequest.GET("/things/1")));
        assertEquals("any", target(router, HttpRequest.DELETE("/things/1")));
        assertEquals("propfind", target(router, HttpRequest.create(HttpMethod.CUSTOM, "/things/1", "PROPFIND")));
        assertEquals("any", target(router, HttpRequest.create(HttpMethod.CUSTOM, "/things/1", "LOCK")));
    }

    @Test
    void theImplicitHeadRouteOfAGetRouteWins() {
        Router router = router(routes -> {
            routes.any("/things", handler("any"));
            routes.GET("/things", handler("get"));
        });

        UriRouteMatch<Object, Object> head = router.findClosest(HttpRequest.HEAD("/things"));
        assertNotNull(head);
        assertTrue(head.getRouteInfo().isImplicitHead());
        assertEquals("get", name(head));
        assertEquals("any", target(router, HttpRequest.OPTIONS("/things")));
    }

    @Test
    void aMoreSpecificAnyRouteWinsOverALessSpecificRouteOfTheMethod() {
        Router router = router(routes -> {
            routes.any("/files/special", handler("any"));
            routes.GET("/files/{name}", handler("get"));
        });

        assertEquals("any", target(router, HttpRequest.GET("/files/special")));
        assertEquals("get", target(router, HttpRequest.GET("/files/other")));
    }

    @Test
    void everyMethodHasARouteOfThePath() {
        Router router = router(routes -> {
            routes.any("/things", handler("any"));
            routes.POST("/things", handler("post"));
        });

        Set<String> methods = router.findAny(HttpRequest.GET("/things")).stream()
            .map(match -> match.getRouteInfo().getHttpMethodName())
            .collect(Collectors.toSet());
        for (HttpMethod method : HttpMethod.values()) {
            if (method != HttpMethod.CUSTOM) {
                assertTrue(methods.contains(method.name()), method.name());
            }
        }
        assertTrue(methods.stream().noneMatch(AnyMethodRoutes.CUSTOM_METHODS::equals), "the custom methods route is not an allowed method");
    }

    @Test
    void theBodyFormAndAsyncVariantsRouteEveryMethod() {
        Router router = router(routes -> {
            routes.any("/body", Argument.STRING, (request, pathVariables, body) -> HttpResponse.ok(body));
            routes.any("/typed", String.class, (request, pathVariables, body) -> HttpResponse.ok(body));
            routes.any("/form", (request, pathVariables, form) -> HttpResponse.ok());
            routes.asyncAny("/async", (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok()));
            routes.asyncAny("/async-body", (request, pathVariables, body) -> CompletableFuture.completedFuture(HttpResponse.ok()));
            routes.path("/prefix", group -> group.any(handler("pathless")));
        });

        for (String path : List.of("/body", "/typed", "/async", "/async-body")) {
            assertNotNull(router.findClosest(HttpRequest.PUT(path, "x").contentType(MediaType.APPLICATION_JSON_TYPE)), path);
            assertNotNull(router.findClosest(HttpRequest.create(HttpMethod.CUSTOM, path, "REPORT")), path);
        }
        assertNotNull(router.findClosest(HttpRequest.PATCH("/form", "a=b").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)));
        assertNull(router.findClosest(HttpRequest.PATCH("/form", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)),
            "a form route consumes the form media types");
        assertEquals("pathless", target(router, HttpRequest.DELETE("/prefix")));
    }

    private static String target(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return name(match);
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
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
