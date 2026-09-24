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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asynchronous locators: the router does not wait for the stage of the locator; it matches the
 * request again when the stage completes, with the target located once per request.
 */
class AsyncLocatorTest {

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void theRequestIsMatchedWhenTheStageCompletesAndTheTargetIsLocatedOnce() {
        CompletableFuture<Object> order = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items/{item}", (request, pathVariables) ->
            HttpResponse.ok(pathVariables.locatedTarget() + " " + pathVariables.getInt("item"))));
        Router router = router(routes -> routes.locateAsync("/orders/{id}", (request, pathVariables) -> {
            calls.incrementAndGet();
            return order;
        }, target -> items));

        HttpRequest<?> request = HttpRequest.GET("/orders/5/items/3");
        CompletionStage<?> pending = pending(() -> router.findClosest(request));
        assertEquals(1, calls.get());
        // still not located: the same stage, the locator is not called again
        assertSame(pending, pending(() -> router.findClosest(request)));
        order.complete("order-5");
        assertTrue(pending.toCompletableFuture().isDone());

        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        assertEquals("order-5", ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target());
        assertEquals("3", match.getVariableValues().get("item"));
        assertEquals("5", match.getVariableValues().get("id"));
        // the allowed methods of the path, with the located target: not located again
        assertTrue(router.findAny(request).stream().anyMatch(any -> any.getRouteInfo().getHttpMethod() == HttpMethod.GET));
        assertEquals(1, calls.get());
        // another request locates again
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/5/items/3")));
        assertEquals(2, calls.get());
    }

    @Test
    void aStageThatCompletesWithNullIsNotFoundAndAFailedOneFails() {
        RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locateAsync("/orders/{id}", (request, pathVariables) -> switch (pathVariables.getInt("id")) {
            case 0 -> CompletableFuture.completedFuture(null);
            case 1 -> CompletableFuture.failedFuture(new IllegalStateException("no order"));
            case 2 -> throw new IllegalArgumentException("bad order");
            default -> CompletableFuture.completedFuture("order");
        }, target -> items));

        // completed stages locate at once
        assertNull(router.findClosest(HttpRequest.GET("/orders/0/items")));
        assertEquals("no order", assertThrows(IllegalStateException.class, () -> router.findClosest(HttpRequest.GET("/orders/1/items"))).getMessage());
        assertEquals("bad order", assertThrows(IllegalArgumentException.class, () -> router.findClosest(HttpRequest.GET("/orders/2/items"))).getMessage());
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/3/items")));
    }

    @Test
    void aLocatedTableLocatesAgainRecursively() {
        CompletableFuture<Object> first = new CompletableFuture<>();
        CompletableFuture<Object> second = new CompletableFuture<>();
        RouteTable[] tree = new RouteTable[1];
        tree[0] = tables.buildLocatedHttpRoutes(node -> {
            node.GET("/leaf", (request, pathVariables) -> HttpResponse.ok(String.valueOf(pathVariables.locatedTarget())));
            node.locateAsync("/sub/{n}", (request, pathVariables) -> pathVariables.getInt("n") == 2 ? second : first, target -> tree[0]);
        });
        Router router = router(routes -> routes.locateAsync("/tree/{root}", (request, pathVariables) ->
            CompletableFuture.completedFuture("root " + pathVariables.getString("root")), target -> tree[0]));

        HttpRequest<?> request = HttpRequest.GET("/tree/r/sub/1/sub/2/leaf");
        CompletionStage<?> pending = pending(() -> router.findClosest(request));
        first.complete("node 1");
        assertTrue(pending.toCompletableFuture().isDone());
        CompletionStage<?> next = pending(() -> router.findClosest(request));
        second.complete("node 2");
        assertTrue(next.toCompletableFuture().isDone());

        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match);
        assertEquals("node 2", ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target());
        assertEquals("r", match.getVariableValues().get("root"));
        assertEquals("2", match.getVariableValues().get("n"));
        assertEquals(HttpMethod.GET, match.getRouteInfo().getHttpMethod());
    }

    @Test
    void findAnyWithAPendingLocatorKnowsNoRouteOfIt() {
        RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> {
            routes.locateAsync("/orders/{id}", (request, pathVariables) -> new CompletableFuture<>(), target -> items);
            routes.POST("/orders/{id}/items", (request, pathVariables) -> HttpResponse.ok());
        });

        List<UriRouteMatch<Object, Object>> any = router.findAny(HttpRequest.DELETE("/orders/1/items"));
        assertEquals(List.of(HttpMethod.POST), any.stream().map(match -> match.getRouteInfo().getHttpMethod()).toList());
    }

    private static CompletionStage<?> pending(org.junit.jupiter.api.function.Executable match) {
        RuntimeException error = assertThrows(RuntimeException.class, match);
        CompletionStage<?> stage = RouteLocator.pendingLocation(error);
        assertNotNull(stage, "an asynchronous locator waits");
        return stage;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
