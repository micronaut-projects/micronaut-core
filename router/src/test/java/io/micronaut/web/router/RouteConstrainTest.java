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
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The constraints on the path variables of handler routes and of groups: a route whose
 * constraints reject the variables of a request is not a match of the request.
 */
class RouteConstrainTest {

    private static final Set<String> SHOPS = Set.of("north", "south");

    @Test
    void aConstraintOnAllThePathVariables() {
        Router router = router(routes -> routes.GET("/shops/{shop}/items/{item}", handler("item"))
            .constrain(variables -> SHOPS.contains(variables.getString("shop")) && variables.getInt("item") < 100));

        assertEquals("item", target(router, HttpRequest.GET("/shops/north/items/5")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/west/items/5")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/north/items/500")));
    }

    @Test
    void aConstraintOnAVariableAsAString() {
        Router router = router(routes -> routes.GET("/files/{name}", handler("file"))
            .constrain("name", name -> !name.startsWith(".")));

        assertEquals("file", target(router, HttpRequest.GET("/files/readme")));
        assertNull(router.findClosest(HttpRequest.GET("/files/.hidden")));
    }

    @Test
    void aConstraintOnAMissingVariableRejects() {
        Router router = router(routes -> routes.GET("/files{/name}", handler("file"))
            .constrain("name", name -> true));

        assertEquals("file", target(router, HttpRequest.GET("/files/readme")));
        assertNull(router.findClosest(HttpRequest.GET("/files")));
    }

    @Test
    void aConstraintOnATypedVariable() {
        Router router = router(routes -> routes.GET("/orders/{id}", handler("order"))
            .constrain("id", Long.class, id -> id > 0));

        assertEquals("order", target(router, HttpRequest.GET("/orders/5")));
        assertNull(router.findClosest(HttpRequest.GET("/orders/-5")));
        // a value that does not convert is rejected, not an error
        assertNull(router.findClosest(HttpRequest.GET("/orders/abc")));
    }

    @Test
    void aConstraintToACollectionOfValues() {
        List<String> values = new ArrayList<>(SHOPS);
        Router router = router(routes -> routes.GET("/shops/{shop}", handler("shop")).constrain("shop", values));
        values.clear(); // copied when declared

        assertEquals("shop", target(router, HttpRequest.GET("/shops/north")));
        assertEquals("shop", target(router, HttpRequest.GET("/shops/south")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/west")));
    }

    @Test
    void aRejectedRequestGoesOnToAnotherRoute() {
        Router router = router(routes -> {
            routes.GET("/items/{id}", handler("by id")).constrain("id", Long.class, id -> true);
            routes.GET("/items/{name}", handler("by name")).constrain("name", name -> !Character.isDigit(name.charAt(0)));
        });

        assertEquals("by id", target(router, HttpRequest.GET("/items/5")));
        assertEquals("by name", target(router, HttpRequest.GET("/items/lamp")));
        assertEquals(1, router.findAllClosest(HttpRequest.GET("/items/5")).size());
    }

    @Test
    void aRejectedValueIsANotFoundNotAMethodNotAllowed() {
        Router router = router(routes -> {
            routes.GET("/shops/{shop}", handler("get")).constrain("shop", SHOPS);
            routes.POST("/shops/{shop}", handler("post")).constrain("shop", SHOPS);
        });

        assertNull(router.findClosest(HttpRequest.DELETE("/shops/west")));
        // no allowed methods for a 405 on an unknown shop, and none for a CORS preflight
        assertTrue(router.findAny(HttpRequest.DELETE("/shops/west")).isEmpty());
        assertTrue(router.findAny("/shops/west", HttpRequest.OPTIONS("/shops/west")).toList().isEmpty());
        assertTrue(router.find(HttpMethod.GET, "/shops/west", null).toList().isEmpty());
        assertTrue(router.route(HttpMethod.GET, "/shops/west").isEmpty());
        // the implicit HEAD route has the constraint of its GET route
        assertNull(router.findClosest(HttpRequest.HEAD("/shops/west")));
        assertNotNull(router.findClosest(HttpRequest.HEAD("/shops/north")));
        // a known shop: the allowed methods
        assertEquals(Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.HEAD), methods(router.findAny(HttpRequest.DELETE("/shops/north"))));
    }

    @Test
    void theConstraintsOfTheGroupsAndOfTheRouteMustAllPass() {
        List<String> evaluated = new ArrayList<>();
        Router router = router(routes -> routes.path("/shops/{shop}", shop -> {
            shop.path("/items/{item}", item -> {
                item.GET("/", handler("item")).constrain("item", Integer.class, i -> {
                    evaluated.add("route");
                    return i < 100;
                });
                item.constrain(variables -> {
                    evaluated.add("inner");
                    return !variables.getString("item").equals("13");
                });
            });
            // declared after the routes of the group
            shop.constrain(variables -> {
                evaluated.add("outer");
                return SHOPS.contains(variables.getString("shop"));
            });
        }));

        assertEquals("item", target(router, HttpRequest.GET("/shops/north/items/5")));
        assertEquals(List.of("outer", "inner", "route"), evaluated, "outer group first");
        assertNull(router.findClosest(HttpRequest.GET("/shops/west/items/5")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/north/items/13")));
        assertNull(router.findClosest(HttpRequest.GET("/shops/north/items/500")));
    }

    @Test
    void aConstraintThatThrowsRejects() {
        Router router = router(routes -> {
            routes.GET("/boom/{id}", handler("boom")).constrain(variables -> {
                throw new IllegalStateException("boom");
            });
            routes.GET("/strict/{id}", handler("strict")).constrain(variables -> variables.getInt("id") > 0);
        });

        assertNull(router.findClosest(HttpRequest.GET("/boom/1")));
        assertEquals("strict", target(router, HttpRequest.GET("/strict/1")));
        assertNull(router.findClosest(HttpRequest.GET("/strict/abc")));
    }

    private static Set<HttpMethod> methods(List<UriRouteMatch<Object, Object>> matches) {
        Set<HttpMethod> methods = new HashSet<>();
        for (UriRouteMatch<Object, Object> match : matches) {
            methods.add(match.getRouteInfo().getHttpMethod());
        }
        return methods;
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
