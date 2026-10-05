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
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The order of handler routes: a tie-break among the routes that are equally good for a request,
 * after the specificity, the media types and the explicit {@code HEAD} routes.
 */
class RouteOrderTest {

    private static final Predicate<HttpRequest<?>> CSV = request -> request.getHeaders().contains("X-Csv");

    @Test
    void theLowestOrderAnswersARequestTwoRoutesOfTheSameTemplateMatch() {
        Router router = router(routes -> {
            routes.GET("/reports/{id}").where(CSV).order(-1).handle(handler("csv"));
            routes.GET("/reports/{id}", handler("any"));
        });

        assertEquals("csv", target(router, HttpRequest.GET("/reports/1").header("X-Csv", "1")));
        assertEquals("any", target(router, HttpRequest.GET("/reports/1")));
    }

    @Test
    void routesWithTheSameOrderStayAmbiguous() {
        Router router = router(routes -> {
            routes.GET("/reports/{id}").order(5).handle(handler("a"));
            routes.GET("/reports/{id}").order(5).handle(handler("b"));
            routes.GET("/reports/{id}").order(7).handle(handler("c"));
        });

        DuplicateRouteException error = assertThrows(DuplicateRouteException.class, () -> router.findClosest(HttpRequest.GET("/reports/1")));
        assertEquals(2, error.getUriRoutes().size(), "the routes with the lowest order are the ambiguous ones");
    }

    @Test
    void theOrderNeverBeatsSpecificity() {
        Router router = router(routes -> {
            routes.GET("/reports/latest").order(10).handle(handler("literal"));
            routes.GET("/reports/{id}").order(-10).handle(handler("variable"));
        });

        assertEquals("literal", target(router, HttpRequest.GET("/reports/latest")));
    }

    @Test
    void theOrderDecidesBetweenAPlainAndAPatternVariable() {
        // equally specific: without an order a request both match is ambiguous
        Router router = router(routes -> {
            routes.GET("/files/{name}").order(10).handle(handler("plain"));
            routes.GET("/files/{name:.+}").order(-10).handle(handler("pattern"));
        });

        assertEquals("pattern", target(router, HttpRequest.GET("/files/a")));
    }

    @Test
    void theOrderNeverBeatsTheMediaTypes() {
        Router router = router(routes -> {
            routes.GET("/reports/{id}").produces(MediaType.TEXT_CSV_TYPE).order(10).handle(handler("csv"));
            routes.GET("/reports/{id}").order(-10).handle(handler("any"));
            routes.POST("/upload").consumes(MediaType.TEXT_PLAIN_TYPE).order(10).handle(handler("text"));
            routes.POST("/upload").consumesAll().order(-10).handle(handler("any"));
        });

        assertEquals("csv", target(router, HttpRequest.GET("/reports/1").accept(MediaType.TEXT_CSV_TYPE)));
        assertEquals("text", target(router, HttpRequest.POST("/upload", "x").contentType(MediaType.TEXT_PLAIN_TYPE)));
    }

    @Test
    void theOrderNeverBeatsAnExplicitHeadRoute() {
        Router router = router(routes -> {
            routes.GET("/items").order(-10).handle(handler("get"));
            routes.route(HttpMethod.HEAD, "/items").order(10).handle(handler("head"));
        });

        assertEquals("head", target(router, HttpRequest.HEAD("/items")));
    }

    @Test
    void theRoutesOfAGroupHaveItsOrderUnlessTheyHaveTheirOwn() {
        Router router = router(routes -> {
            routes.group(fallbacks -> {
                fallbacks.GET("/pages/{name}", handler("fallback"));
                fallbacks.path("/", nested -> nested.GET("/docs/{name}", handler("nested fallback")));
                fallbacks.GET("/own/{name}").order(-20).handle(handler("own"));
                // declared after the routes
                fallbacks.order(100);
            });
            routes.GET("/pages/{name}", handler("page"));
            routes.GET("/docs/{name}", handler("doc"));
            routes.GET("/own/{name}", handler("default"));
        });

        assertEquals("page", target(router, HttpRequest.GET("/pages/a")));
        assertEquals("doc", target(router, HttpRequest.GET("/docs/a")));
        assertEquals("own", target(router, HttpRequest.GET("/own/a")));
    }

    @Test
    void aDeclaredRouteIsOrderedBeforeItIsBuilt() {
        Router router = router(routes -> {
            routes.GET("/declared/{id}").order(1).handle(handler("first"));
            routes.group(group -> {
                group.order(-1);
                group.GET("/declared/{name}").handle(handler("second"));
            });
        });

        List<Integer> orders = router.uriRoutes()
            .filter(route -> route.getHttpMethod() == HttpMethod.GET)
            .map(UriRouteInfo::getOrder)
            .sorted()
            .toList();
        assertEquals(List.of(-1, 1), orders);
        assertEquals("second", target(router, HttpRequest.GET("/declared/5")));
        // the implicit HEAD routes have the orders of their GET routes
        UriRouteMatch<Object, Object> head = router.findClosest(HttpRequest.HEAD("/declared/5"));
        assertNotNull(head);
        assertEquals(-1, head.getRouteInfo().getOrder());
    }

    @Test
    void aRouteOfSeveralMethodsHasTheOrderForEachMethod() {
        Router router = router(routes -> {
            routes.route(java.util.Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/both").order(-1).handle(handler("ordered"));
            routes.PUT("/both", handler("put"));
            routes.PATCH("/both", handler("patch"));
        });

        assertEquals("ordered", target(router, HttpRequest.PUT("/both", "")));
        assertEquals("ordered", target(router, HttpRequest.PATCH("/both", "")));
    }

    private static String target(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
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
