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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route locator runs at most once per request and level, synchronous or not: matching the
 * request again, e.g. to find the allowed methods of a {@code 405}, reuses what it located.
 */
class LocatorOncePerRequestTest {

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void aSynchronousLocatorRunsOnceWhenTheAllowedMethodsAreLookedUp() {
        AtomicInteger calls = new AtomicInteger();
        RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> {
            calls.incrementAndGet();
            return "order";
        }, target -> items));

        HttpRequest<?> request = HttpRequest.DELETE("/orders/5/items");
        assertNull(router.findClosest(request), "no DELETE route in the table of the target");
        // the 405: the allowed methods, with the target located for the request already
        assertTrue(router.findAny(request).stream().anyMatch(any -> any.getRouteInfo().getHttpMethod() == HttpMethod.GET));
        assertEquals(1, calls.get());

        // another request locates again, once
        HttpRequest<?> another = HttpRequest.GET("/orders/5/items");
        assertNotNull(router.findClosest(another));
        assertEquals(1, router.findAllClosest(another).size());
        assertEquals(2, calls.get());
    }

    @Test
    void anAsynchronousLocatorRunsOnceWhenTheAllowedMethodsAreLookedUp() {
        AtomicInteger calls = new AtomicInteger();
        RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locateAsync("/orders/{id}", (request, pathVariables) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("order");
        }, target -> items));

        HttpRequest<?> request = HttpRequest.DELETE("/orders/5/items");
        assertNull(router.findClosest(request));
        assertTrue(router.findAny(request).stream().anyMatch(any -> any.getRouteInfo().getHttpMethod() == HttpMethod.GET));
        assertEquals(1, calls.get());
    }

    @Test
    void aSynchronousLocatorThatFailsRunsOnceAndFailsTheSameWay() {
        AtomicInteger calls = new AtomicInteger();
        RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("no order");
        }, target -> items));

        HttpRequest<?> request = HttpRequest.GET("/orders/5/items");
        IllegalStateException first = assertThrows(IllegalStateException.class, () -> router.findClosest(request));
        IllegalStateException second = assertThrows(IllegalStateException.class, () -> router.findAny(request));
        assertSame(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void aNestedSynchronousLocatorRunsOncePerLevel() {
        AtomicInteger roots = new AtomicInteger();
        AtomicInteger nodes = new AtomicInteger();
        RouteTable[] tree = new RouteTable[1];
        tree[0] = tables.buildLocatedHttpRoutes(node -> {
            node.GET("/leaf", (request, pathVariables) -> HttpResponse.ok());
            node.locate("/sub/{n}", (request, pathVariables) -> {
                nodes.incrementAndGet();
                return "node " + pathVariables.getInt("n");
            }, target -> tree[0]);
        });
        Router router = router(routes -> routes.locate("/tree/{root}", (request, pathVariables) -> {
            roots.incrementAndGet();
            return "root";
        }, target -> tree[0]));

        HttpRequest<?> request = HttpRequest.DELETE("/tree/r/sub/1/sub/2/leaf");
        assertNull(router.findClosest(request));
        assertTrue(router.findAny(request).stream().anyMatch(any -> any.getRouteInfo().getHttpMethod() == HttpMethod.GET));
        assertEquals(1, roots.get());
        // one call for each of the two levels
        assertEquals(2, nodes.get());
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
