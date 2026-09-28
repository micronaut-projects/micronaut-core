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
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.http.PathVariables;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The asynchronous handlers with and without the body are the {@code handleAsync} terminals of a
 * route and of its body stage {@code body()}: an {@code AsyncRequestHandler} takes two parameters,
 * an {@code AsyncBodyRequestHandler} three, for a lambda or the method a method reference names.
 * Each declaration below is routed to the handler it names, which the arguments of its route show,
 * for every creator: per method, by method, for a set of methods and by method name.
 */
class AsyncHandlerOverloadsTest {

    private static final List<Class<?>> NO_BODY = List.of(HttpRequest.class, PathVariables.class);
    private static final List<Class<?>> BODY = List.of(HttpRequest.class, PathVariables.class, AsyncRequestBody.class);

    @Test
    void lambdasAreRoutedToTheirHandler() {
        Router router = router(routes -> {
            routes.GET("/get").handleAsync((request, pathVariables) -> ok());
            routes.GET("/get-body").body().handleAsync((request, pathVariables, body) -> body.text().thenApply(HttpResponse::ok));
            routes.POST("/post").handleAsync((request, pathVariables) -> ok());
            routes.POST("/post-body").body().handleAsync((request, pathVariables, body) -> body.text().thenApply(HttpResponse::ok));
            routes.PUT("/put").handleAsync((request, pathVariables) -> ok());
            routes.PUT("/put-body").body().handleAsync((request, pathVariables, body) -> body.discardBody().thenApply(done -> HttpResponse.ok()));
            routes.PATCH("/patch").handleAsync((request, pathVariables) -> ok());
            routes.PATCH("/patch-body").body().handleAsync((request, pathVariables, body) -> ok());
            routes.DELETE("/delete").handleAsync((request, pathVariables) -> ok());
            routes.DELETE("/delete-body").body().handleAsync((request, pathVariables, body) -> ok());
            routes.route(HttpMethod.POST, "/method").handleAsync((request, pathVariables) -> ok());
            routes.route(HttpMethod.POST, "/method-body").body().handleAsync((request, pathVariables, body) -> ok());
            routes.route(Set.of(HttpMethod.PUT), "/methods").handleAsync((request, pathVariables) -> ok());
            routes.route(Set.of(HttpMethod.PUT), "/methods-body").body().handleAsync((request, pathVariables, body) -> ok());
            routes.route("PROPFIND", "/named").handleAsync((request, pathVariables) -> ok());
            routes.route("PROPFIND", "/named-body").body().handleAsync((request, pathVariables, body) -> ok());
            routes.route("POST", "/declared").handleAsync((request, pathVariables) -> ok());
            routes.route("POST", "/declared-body").body().handleAsync((request, pathVariables, body) -> ok());
        });
        assertArguments(NO_BODY, router, HttpRequest.GET("/get"));
        assertArguments(BODY, router, HttpRequest.GET("/get-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/post", ""));
        assertArguments(BODY, router, HttpRequest.POST("/post-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/put", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/put-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PATCH("/patch", ""));
        assertArguments(BODY, router, HttpRequest.PATCH("/patch-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.DELETE("/delete"));
        assertArguments(BODY, router, HttpRequest.DELETE("/delete-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/method", ""));
        assertArguments(BODY, router, HttpRequest.POST("/method-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/methods", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/methods-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named", "PROPFIND"));
        assertArguments(BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named-body", "PROPFIND"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/declared", ""));
        assertArguments(BODY, router, HttpRequest.POST("/declared-body", ""));
    }

    @Test
    void methodReferencesAreRoutedToTheirHandler() {
        Handlers handlers = new Handlers();
        Router router = router(routes -> {
            routes.GET("/static").handleAsync(AsyncHandlerOverloadsTest::withoutBody);
            routes.POST("/static-body").body().handleAsync(AsyncHandlerOverloadsTest::withBody);
            routes.GET("/bound").handleAsync(handlers::find);
            routes.POST("/bound-body").body().handleAsync(handlers::save);
            routes.route(HttpMethod.PUT, "/method").handleAsync(AsyncHandlerOverloadsTest::withoutBody);
            routes.route(HttpMethod.PUT, "/method-body").body().handleAsync(handlers::save);
            routes.route("PROPFIND", "/named").handleAsync(handlers::find);
            routes.route("PROPFIND", "/named-body").body().handleAsync(AsyncHandlerOverloadsTest::withBody);
            routes.route("DELETE", "/declared").handleAsync(handlers::find);
            routes.route("DELETE", "/declared-body").body().handleAsync(handlers::save);
        });
        assertArguments(NO_BODY, router, HttpRequest.GET("/static"));
        assertArguments(BODY, router, HttpRequest.POST("/static-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.GET("/bound"));
        assertArguments(BODY, router, HttpRequest.POST("/bound-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/method", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/method-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named", "PROPFIND"));
        assertArguments(BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named-body", "PROPFIND"));
        assertArguments(NO_BODY, router, HttpRequest.DELETE("/declared"));
        assertArguments(BODY, router, HttpRequest.DELETE("/declared-body"));
    }

    @Test
    void theRoutesAtThePathOfAGroupToo() {
        Router router = router(routes -> {
            routes.path("/get", group -> group.GET("/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/get-body", group -> group.GET("/").body().handleAsync((request, pathVariables, body) -> ok()));
            routes.path("/post", group -> group.POST("/").handleAsync(AsyncHandlerOverloadsTest::withoutBody));
            routes.path("/post-body", group -> group.POST("/").body().handleAsync(AsyncHandlerOverloadsTest::withBody));
            routes.path("/put", group -> group.PUT("/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/put-body", group -> group.PUT("/").body().handleAsync((request, pathVariables, body) -> ok()));
            routes.path("/patch", group -> group.PATCH("/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/patch-body", group -> group.PATCH("/").body().handleAsync((request, pathVariables, body) -> ok()));
            routes.path("/delete", group -> group.DELETE("/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/delete-body", group -> group.DELETE("/").body().handleAsync((request, pathVariables, body) -> ok()));
            routes.path("/method", group -> group.route(HttpMethod.POST, "/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/method-body", group -> group.route(HttpMethod.POST, "/").body().handleAsync((request, pathVariables, body) -> ok()));
            routes.path("/methods", group -> group.route(Set.of(HttpMethod.PUT), "/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/methods-body", group -> group.route(Set.of(HttpMethod.PUT), "/").body().handleAsync((request, pathVariables, body) -> ok()));
            routes.path("/named", group -> group.route("PROPFIND", "/").handleAsync((request, pathVariables) -> ok()));
            routes.path("/named-body", group -> group.route("PROPFIND", "/").body().handleAsync((request, pathVariables, body) -> ok()));
        });
        assertArguments(NO_BODY, router, HttpRequest.GET("/get"));
        assertArguments(BODY, router, HttpRequest.GET("/get-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/post", ""));
        assertArguments(BODY, router, HttpRequest.POST("/post-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/put", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/put-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PATCH("/patch", ""));
        assertArguments(BODY, router, HttpRequest.PATCH("/patch-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.DELETE("/delete"));
        assertArguments(BODY, router, HttpRequest.DELETE("/delete-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/method", ""));
        assertArguments(BODY, router, HttpRequest.POST("/method-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/methods", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/methods-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named", "PROPFIND"));
        assertArguments(BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named-body", "PROPFIND"));
    }

    private static CompletionStage<HttpResponse<?>> ok() {
        return CompletableFuture.completedFuture(HttpResponse.ok());
    }

    private static CompletionStage<? extends HttpResponse<?>> withoutBody(HttpRequest<?> request, PathVariables pathVariables) {
        return ok();
    }

    private static CompletionStage<? extends HttpResponse<?>> withBody(HttpRequest<?> request, PathVariables pathVariables, AsyncRequestBody body) {
        return ok();
    }

    private static void assertArguments(List<Class<?>> expected, Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        List<Class<?>> types = Arrays.stream(match.getRouteInfo().getTargetMethod().getArguments()).<Class<?>>map(Argument::getType).toList();
        assertEquals(expected, types, request.getMethodName() + " " + request.getPath());
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    static final class Handlers {
        CompletionStage<? extends HttpResponse<?>> find(HttpRequest<?> request, PathVariables pathVariables) {
            return ok();
        }

        CompletionStage<? extends HttpResponse<?>> save(HttpRequest<?> request, PathVariables pathVariables, AsyncRequestBody body) {
            return body.text().thenApply(HttpResponse::ok);
        }
    }
}
