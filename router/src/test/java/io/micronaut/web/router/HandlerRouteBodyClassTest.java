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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.web.router.builder.BodyRequestHandler;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The body type of a handler route given as a class, next to the variants taking an
 * {@link Argument}, and the response type given as a class.
 */
class HandlerRouteBodyClassTest {

    private static final RouteTableFactory TABLES = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void theBodyTypeAsAClassIsTheArgumentOfTheClass() {
        RouteDeclaration declared = RouteDeclaration.of(HttpMethod.POST, "/declared");
        Router router = router(routes -> {
            routes.POST("/post", Item.class, (request, pathVariables, item) -> HttpResponse.ok(item.name()));
            routes.PUT("/put", Item.class, HandlerRouteBodyClassTest::handle);
            routes.PATCH("/patch", Item.class, (request, pathVariables, item) -> HttpResponse.ok(item.name()));
            routes.PATCH("/patch-argument", Argument.of(Item.class), (request, pathVariables, item) -> HttpResponse.ok(item.name()));
            routes.handle(HttpMethod.DELETE, "/delete", Item.class, (request, pathVariables, item) -> HttpResponse.ok());
            routes.handle("PROPFIND", "/propfind", Item.class, (request, pathVariables, item) -> HttpResponse.ok());
            routes.handle(declared, Item.class, (request, pathVariables, item) -> HttpResponse.ok());
            RouteTable table = TABLES.buildLocatedHttpRoutes(Item.class, items -> {
                items.handle(HttpMethod.POST, "/body", Item.class, (request, pathVariables, target, item) -> HttpResponse.ok());
                items.handle(RouteDeclaration.of(HttpMethod.PUT, "/declared-body"), Item.class, (request, pathVariables, target, item) -> HttpResponse.ok());
            });
            routes.locate("/located", (request, pathVariables) -> new Item("t"), target -> table);
        });
        for (HttpRequest<?> request : List.of(HttpRequest.POST("/post", ""), HttpRequest.PUT("/put", ""),
            HttpRequest.PATCH("/patch", ""), HttpRequest.PATCH("/patch-argument", ""), HttpRequest.DELETE("/delete", ""),
            HttpRequest.create(HttpMethod.CUSTOM, "/propfind", "PROPFIND"), HttpRequest.POST("/declared", ""))) {
            MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) route(router, request);
            assertEquals(Item.class, route.getRequestBodyType().orElseThrow().getType(), request.getMethodName() + " " + request.getPath());
        }
    }

    @Test
    void theResponseTypeAsAClass() {
        Router router = router(routes -> {
            routes.GET("/typed", (request, pathVariables) -> HttpResponse.ok(new Item("x"))).responseType(Item.class);
            routes.error(IllegalStateException.class, (request, error) -> HttpResponse.ok()).responseType(Item.class);
            routes.status(HttpStatus.NOT_FOUND, request -> HttpResponse.notFound()).responseType(Item.class);
        });
        assertEquals(Item.class, route(router, HttpRequest.GET("/typed")).getResponseBodyType().getType());
    }

    @Test
    void theClassIsRequired() {
        router(routes -> {
            assertThrows(NullPointerException.class, () -> routes.POST("/x", (Class<Item>) null, (request, pathVariables, item) -> HttpResponse.ok()));
            assertThrows(NullPointerException.class, () -> routes.GET("/y", (request, pathVariables) -> HttpResponse.ok()).responseType((Class<?>) null));
        });
    }

    private static HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables, Item item) {
        return HttpResponse.ok(item.name());
    }

    private static RouteInfo<?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    record Item(String name) {
    }

    /**
     * The handler type of the method reference, for the overload resolution.
     */
    static final BodyRequestHandler<Item> REFERENCE = HandlerRouteBodyClassTest::handle;
}
