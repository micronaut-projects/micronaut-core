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
import io.micronaut.http.form.FormData;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.BodyRequestHandler;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.FormRequestHandler;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import io.micronaut.http.AsyncServerHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Routes declared without a path, like a controller method mapped without a URI: at the prefix
 * of the group, at the prefix of the locator in a located table, or at the root. Every shortcut
 * compiles with lambdas and method references, next to the forms with a path.
 */
class HandlerRoutePathlessTest {

    private static final RouteTableFactory TABLES = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void aRouteWithoutAPathIsAtThePrefixOfItsGroup() {
        Router router = router(routes -> routes.path("/users", users -> {
            users.GET((request, pathVariables) -> HttpResponse.ok("list"));
            users.POST(Item.class, (request, pathVariables, item) -> HttpResponse.ok());
            users.GET("/{id}", (request, pathVariables) -> HttpResponse.ok());
            users.path("/admins", admins -> admins.DELETE((request, pathVariables) -> HttpResponse.ok()));
        }), uri -> uri);
        assertEquals("/users", route(router, HttpRequest.GET("/users")).getUriMatchTemplate().toString());
        assertNotNull(router.findClosest(HttpRequest.POST("/users", "")));
        assertNotNull(router.findClosest(HttpRequest.DELETE("/users/admins")));
        // the implicit HEAD route of the GET route
        assertNotNull(router.findClosest(HttpRequest.HEAD("/users")));
        assertNull(router.findClosest(HttpRequest.PUT("/users", "")));
    }

    @Test
    void aRouteWithoutAPathIsAtTheRoot() {
        Router router = router(routes -> routes.GET((request, pathVariables) -> HttpResponse.ok("root")), uri -> uri);
        assertEquals("/", route(router, HttpRequest.GET("/")).getUriMatchTemplate().toString());
    }

    @Test
    void aRouteWithoutAPathIsUnderTheContextPath() {
        Router router = router(routes -> {
            routes.GET((request, pathVariables) -> HttpResponse.ok("root"));
            routes.path("/users", users -> users.GET((request, pathVariables) -> HttpResponse.ok("list")));
        }, uri -> RouteAssembly.underContextPath("/cp", uri));
        assertEquals(RouteAssembly.underContextPath("/cp", "/"), route(router, HttpRequest.GET("/cp")).getUriMatchTemplate().toString());
        assertEquals("/cp/users", route(router, HttpRequest.GET("/cp/users")).getUriMatchTemplate().toString());
    }

    @Test
    void aRouteWithoutAPathInALocatedTableIsAtThePrefixOfTheLocator() {
        RouteTable table = TABLES.buildLocatedHttpRoutes(Item.class, item -> {
            item.handle(HttpMethod.GET, (request, pathVariables, target) -> HttpResponse.ok("item " + target.name()));
            item.handle(HttpMethod.PUT, Item.class, (request, pathVariables, target, body) -> HttpResponse.ok());
            item.GET("/details", (request, pathVariables) -> HttpResponse.ok("details"));
        });
        Router router = router(routes -> routes.locate("/items/{name}", (request, pathVariables) -> new Item(pathVariables.getString("name")),
            target -> table), uri -> uri);
        assertNotNull(router.findClosest(HttpRequest.GET("/items/a")));
        assertNotNull(router.findClosest(HttpRequest.PUT("/items/a", "")));
        assertNotNull(router.findClosest(HttpRequest.GET("/items/a/details")));
    }

    /**
     * Every shortcut, with and without a path, with lambdas and method references: the overloads
     * are not ambiguous.
     */
    @Test
    void everyShortcutCompilesWithLambdasAndMethodReferences() {
        Router router = router(routes -> routes.path("/all", all -> {
            all.GET((request, pathVariables) -> HttpResponse.ok());
            all.GET(HandlerRoutePathlessTest::plain);
            all.GET("/p", (request, pathVariables) -> HttpResponse.ok());
            all.GET("/p", HandlerRoutePathlessTest::plain);
            all.POST((request, pathVariables) -> HttpResponse.ok());
            all.POST(HandlerRoutePathlessTest::plain);
            all.POST("/p", (request, pathVariables) -> HttpResponse.ok());
            all.POST("/p", HandlerRoutePathlessTest::plain);
            all.PUT((request, pathVariables) -> HttpResponse.ok());
            all.PUT(HandlerRoutePathlessTest::plain);
            all.PATCH((request, pathVariables) -> HttpResponse.ok());
            all.PATCH(HandlerRoutePathlessTest::plain);
            all.DELETE((request, pathVariables) -> HttpResponse.ok());
            all.DELETE(HandlerRoutePathlessTest::plain);

            all.path("/body", body -> {
                body.POST(Item.class, (request, pathVariables, item) -> HttpResponse.ok());
                body.POST(Item.class, HandlerRoutePathlessTest::body);
                body.POST(Argument.of(Item.class), (request, pathVariables, item) -> HttpResponse.ok());
                body.POST(Argument.of(Item.class), HandlerRoutePathlessTest::body);
                body.POST("/p", Item.class, (request, pathVariables, item) -> HttpResponse.ok());
                body.POST("/p", Item.class, HandlerRoutePathlessTest::body);
                body.POST("/p", Argument.of(Item.class), HandlerRoutePathlessTest::body);
                body.PUT(Item.class, (request, pathVariables, item) -> HttpResponse.ok());
                body.PUT(Argument.of(Item.class), HandlerRoutePathlessTest::body);
                body.PATCH(Item.class, (request, pathVariables, item) -> HttpResponse.ok());
                body.PATCH(Argument.of(Item.class), HandlerRoutePathlessTest::body);
            });
            all.path("/form", form -> {
                form.POST((request, pathVariables, data) -> HttpResponse.ok());
                form.POST(HandlerRoutePathlessTest::form);
                form.POST("/p", (request, pathVariables, data) -> HttpResponse.ok());
                form.POST("/p", HandlerRoutePathlessTest::form);
                form.PUT((request, pathVariables, data) -> HttpResponse.ok());
                form.PUT(HandlerRoutePathlessTest::form);
            });
            all.path("/async", async -> {
                async.asyncGET((request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok()));
                async.asyncGET(HandlerRoutePathlessTest::async);
                async.asyncPOST(HandlerRoutePathlessTest::async);
                async.asyncPUT(HandlerRoutePathlessTest::async);
                async.asyncPATCH(HandlerRoutePathlessTest::async);
                async.asyncDELETE(HandlerRoutePathlessTest::async);
                async.asyncGET("/p", HandlerRoutePathlessTest::async);
            });
            all.path("/method", method -> {
                method.handle(HttpMethod.OPTIONS, (request, pathVariables) -> HttpResponse.ok());
                method.handle(HttpMethod.OPTIONS, "/p", HandlerRoutePathlessTest::plain);
                method.handle(HttpMethod.POST, Item.class, HandlerRoutePathlessTest::body);
                method.handle(HttpMethod.PUT, Argument.of(Item.class), (request, pathVariables, item) -> HttpResponse.ok());
                method.handleForm(HttpMethod.PATCH, HandlerRoutePathlessTest::form);
                method.handleAsync(HttpMethod.DELETE, HandlerRoutePathlessTest::async);
                method.handle(Set.of(HttpMethod.TRACE), HandlerRoutePathlessTest::plain);
                method.handleAsync(Set.of(HttpMethod.GET), (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok()));
            });
            all.path("/named", named -> {
                named.handle("PROPFIND", (request, pathVariables) -> HttpResponse.ok());
                named.handle("PROPFIND", "/p", HandlerRoutePathlessTest::plain);
                named.handle("PROPPATCH", Item.class, HandlerRoutePathlessTest::body);
                named.handle("COPY", Argument.of(Item.class), (request, pathVariables, item) -> HttpResponse.ok());
                named.handleAsync("MOVE", HandlerRoutePathlessTest::async);
                named.handleForm("LOCK", HandlerRoutePathlessTest::form);
            });
        }), uri -> uri);
        for (HttpRequest<?> request : List.of(HttpRequest.GET("/all"), HttpRequest.GET("/all/p"), HttpRequest.POST("/all/body", ""),
            HttpRequest.PATCH("/all/body", ""), HttpRequest.DELETE("/all/async"), HttpRequest.OPTIONS("/all/method"),
            HttpRequest.create(HttpMethod.CUSTOM, "/all/named", "PROPFIND"), HttpRequest.create(HttpMethod.CUSTOM, "/all/named", "LOCK"))) {
            assertFalse(router.findAllClosest(request).isEmpty(), request.getMethodName() + " " + request.getPath());
        }
    }

    private static HttpResponse<?> plain(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static HttpResponse<?> body(HttpRequest<?> request, PathVariables pathVariables, Item item) {
        return HttpResponse.ok();
    }

    private static HttpResponse<?> form(HttpRequest<?> request, PathVariables pathVariables, FormData form) {
        return HttpResponse.ok();
    }

    private static CompletionStage<? extends HttpResponse<?>> async(AsyncServerHttpRequest<?> request, PathVariables pathVariables) {
        return CompletableFuture.completedFuture(HttpResponse.ok());
    }

    private static UriRouteInfo<?, ?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes, UnaryOperator<String> routeUri) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, routeUri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    record Item(String name) {
    }

    // the handler types of the method references
    static final RequestHandler PLAIN = HandlerRoutePathlessTest::plain;
    static final BodyRequestHandler<Item> BODY = HandlerRoutePathlessTest::body;
    static final FormRequestHandler FORM = HandlerRoutePathlessTest::form;
    static final AsyncRequestHandler ASYNC = HandlerRoutePathlessTest::async;
}
