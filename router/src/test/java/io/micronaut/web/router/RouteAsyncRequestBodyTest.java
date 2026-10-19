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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a route takes its body as an {@link AsyncRequestBody}, computed when the route is built.
 */
class RouteAsyncRequestBodyTest {

    static final String SPEC_NAME = "RouteAsyncRequestBodyTest";

    @Test
    void aHandlerRouteThatReadsItsBodyAsynchronouslyTakesAnAsyncRequestBody() {
        Router router = router(routes -> {
            routes.POST("/async").body().handleAsync((request, pathVariables, body) -> CompletableFuture.completedFuture(HttpResponse.ok()));
            routes.POST("/sync").body(String.class).handle((request, pathVariables, body) -> HttpResponse.ok());
            routes.GET("/none").handle((request, pathVariables) -> HttpResponse.ok());
        });
        assertTrue(route(router, HttpRequest.POST("/async", "")).isAsyncRequestBody());
        assertFalse(route(router, HttpRequest.POST("/sync", "")).isAsyncRequestBody());
        assertFalse(route(router, HttpRequest.GET("/none")).isAsyncRequestBody());
    }

    @Test
    void aControllerRouteTakesAnAsyncRequestBodyWhenItDeclaresOne() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            Router router = context.getBean(Router.class);
            assertTrue(route(router, HttpRequest.POST("/async-request-body/async", "")).isAsyncRequestBody());
            assertFalse(route(router, HttpRequest.POST("/async-request-body/body", "")).isAsyncRequestBody());
        }
    }

    @Test
    void theDefaultLooksAtTheArgumentsOfTheTargetMethod() {
        Router router = router(routes -> {
            routes.POST("/async").body().handleAsync((request, pathVariables, body) -> CompletableFuture.completedFuture(HttpResponse.ok()));
            routes.GET("/none").handle((request, pathVariables) -> HttpResponse.ok());
        });
        assertTrue(defaultOf(route(router, HttpRequest.POST("/async", ""))).isAsyncRequestBody());
        assertFalse(defaultOf(route(router, HttpRequest.GET("/none"))).isAsyncRequestBody());
    }

    private static MethodBasedRouteInfo<?, ?> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return (MethodBasedRouteInfo<?, ?>) match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    /**
     * A route info that keeps the default of {@link MethodBasedRouteInfo#isAsyncRequestBody()}.
     */
    private static MethodBasedRouteInfo<?, ?> defaultOf(MethodBasedRouteInfo<?, ?> route) {
        return (MethodBasedRouteInfo<?, ?>) Proxy.newProxyInstance(MethodBasedRouteInfo.class.getClassLoader(),
            new Class<?>[]{MethodBasedRouteInfo.class}, (proxy, method, args) -> method.isDefault()
                ? InvocationHandler.invokeDefault(proxy, method, args)
                : method.invoke(route, args));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/async-request-body")
    static class AsyncBodyController {

        @Post("/async")
        String async(AsyncRequestBody body) {
            return "async";
        }

        @Post("/body")
        String body(@Body String body) {
            return body;
        }
    }
}
