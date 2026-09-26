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
import io.micronaut.web.router.builder.ClosedRouteBuilderTest;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.http.PathVariables;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of a {@link LocatedRoutes} is built when the first target it routes is located, once,
 * and shared by every locator route that answers the same instance.
 */
class LocatedRoutesTableTest {

    @Test
    void theTableIsNotBuiltBeforeATargetIsLocated() {
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes -> routes.GET("/items", LocatedRoutesTableTest::ok));
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> pathVariables.getLong("id"), items));
        assertEquals(0, items.declared.get());

        assertNull(router.findClosest(HttpRequest.GET("/other")));
        assertEquals(0, items.declared.get());

        assertNotNull(router.findClosest(HttpRequest.GET("/orders/1/items")));
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/2/items")));
        assertEquals(1, items.declared.get());
    }

    @Test
    void theTableIsBuiltExactlyOnceUnderConcurrentFirstUse() throws Exception {
        int threads = 16;
        CountDownLatch declaring = new CountDownLatch(1);
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes -> {
            // slow enough for the other threads to ask for the table while it is built
            try {
                declaring.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            routes.GET("/items", LocatedRoutesTableTest::ok);
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> pathVariables.getLong("id"), items));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<UriRouteMatch<Object, Object>>> matches = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                long id = i;
                matches.add(executor.submit(() -> {
                    start.await();
                    return router.findClosest(HttpRequest.GET("/orders/" + id + "/items"));
                }));
            }
            start.countDown();
            for (Future<UriRouteMatch<Object, Object>> match : matches) {
                assertNotNull(match.get(10, TimeUnit.SECONDS));
            }
        } finally {
            declaring.countDown();
            executor.shutdownNow();
        }
        assertEquals(1, items.declared.get());
    }

    @Test
    void theLocatorRoutesOfTheSameLocatedRoutesShareTheTable() {
        TestLocatedRoutes<Long> items = TestLocatedRoutes.of(Long.class, routes ->
            routes.handle(HttpMethod.GET, "/items", (request, pathVariables, id) -> HttpResponse.ok(id)));
        Router router = router(routes -> {
            routes.locate("/orders/{id}", (request, pathVariables) -> pathVariables.getLong("id"), items);
            routes.locateAsync("/async/{id}", (request, pathVariables) -> CompletableFuture.completedFuture(pathVariables.getLong("id")),
                target -> items);
            routes.path("/shop", shop -> shop.locate("/carts/{id}", (request, pathVariables) -> pathVariables.getLong("id"), target -> items));
        });

        UriRouteMatch<Object, Object> order = router.findClosest(HttpRequest.GET("/orders/1/items"));
        UriRouteMatch<Object, Object> async = router.findClosest(HttpRequest.GET("/async/2/items"));
        UriRouteMatch<Object, Object> cart = router.findClosest(HttpRequest.GET("/shop/carts/3/items"));
        assertNotNull(order);
        assertNotNull(async);
        assertNotNull(cart);
        assertEquals(1, items.declared.get());
        // the same route of the same table
        assertSame(order.getRouteInfo(), async.getRouteInfo());
        assertSame(order.getRouteInfo(), cart.getRouteInfo());
    }

    @Test
    void equalInstancesOfLocatedRoutesHaveTheirOwnTables() {
        EqualRoutes first = new EqualRoutes();
        EqualRoutes second = new EqualRoutes();
        Router router = router(routes -> {
            routes.locate("/first/{id}", (request, pathVariables) -> pathVariables.getLong("id"), first);
            routes.locate("/second/{id}", (request, pathVariables) -> pathVariables.getLong("id"), second);
        });
        assertNotNull(router.findClosest(HttpRequest.GET("/first/1/items")));
        assertNotNull(router.findClosest(HttpRequest.GET("/second/1/items")));
        assertEquals(1, first.declared);
        assertEquals(1, second.declared);
    }

    @Test
    void theFixedSetShortcutRoutesEveryTargetToTheSameRoutes() {
        LocatedRoutes<Order> orderRoutes = new LocatedRoutes<>() {
            @Override
            public Argument<Order> targetType() {
                return Argument.of(Order.class);
            }

            @Override
            public void routes(LocatedHttpRouteBuilder<Order> routes) {
                assertEquals(Argument.of(Order.class), routes.targetType());
                routes.handle(HttpMethod.GET, "/id", (request, pathVariables, order) -> HttpResponse.ok(order.id()));
            }
        };
        Router router = router(routes -> {
            routes.locate("/orders/{id}", (request, pathVariables) -> {
                long id = pathVariables.getLong("id");
                return id == 0 ? null : new Order(id);
            }, orderRoutes);
            routes.locateAsync("/async/{id}", (request, pathVariables) ->
                CompletableFuture.completedFuture(new Order(pathVariables.getLong("id"))), orderRoutes);
        });

        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/orders/5/id"));
        assertNotNull(match);
        assertEquals("5", match.getVariableValues().get("id"));
        assertEquals(new Order(5), ((DynamicRouteTarget.ResolvedMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target());
        assertNotNull(router.findClosest(HttpRequest.GET("/async/6/id")));
        // no target: no route
        assertNull(router.findClosest(HttpRequest.GET("/orders/0/id")));
    }

    @Test
    void locatedRoutesThatFailToDeclareAreDeclaredAgainForTheNextTarget() {
        AtomicReference<Boolean> fail = new AtomicReference<>(true);
        TestLocatedRoutes<Object> items = TestLocatedRoutes.of(routes -> {
            if (fail.get()) {
                throw new IllegalStateException("not yet");
            }
            routes.GET("/items", LocatedRoutesTableTest::ok);
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> pathVariables.getLong("id"), items));

        assertThrows(IllegalStateException.class, () -> router.findClosest(HttpRequest.GET("/orders/1/items")));
        fail.set(false);
        assertNotNull(router.findClosest(HttpRequest.GET("/orders/1/items")));
        assertEquals(2, items.declared.get());
    }

    @Test
    void theBuilderOfLocatedRoutesIsClosedWhenTheyReturned() {
        AtomicReference<HttpRouteBuilder> kept = new AtomicReference<>();
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        tables.table(TestLocatedRoutes.of(routes -> {
            kept.set(routes);
            routes.GET("/declared", LocatedRoutesTableTest::ok);
        }));
        ClosedRouteBuilderTest.assertEveryDeclarationFails(kept.get());
    }

    @Test
    void theBuilderOfLocatedRoutesIsClosedWhenTheyFail() {
        AtomicReference<HttpRouteBuilder> kept = new AtomicReference<>();
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        assertThrows(UnsupportedOperationException.class, () -> tables.table(TestLocatedRoutes.of(routes -> {
            kept.set(routes);
            throw new UnsupportedOperationException("failed");
        })));
        ClosedRouteBuilderTest.assertClosed(() -> kept.get().GET("/late", LocatedRoutesTableTest::ok));
    }

    @Test
    void locatedRoutesCannotDeclareGlobalErrorRoutesServerFiltersOrPorts() {
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> tables.table(TestLocatedRoutes.of(routes ->
            routes.error(IllegalStateException.class, (request, e) -> HttpResponse.serverError()))));
        assertTrue(error.getMessage().contains("error routes"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> tables.table(TestLocatedRoutes.of(routes ->
            routes.filter("/**").before(request -> { }))));
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    record Order(long id) {
    }

    /**
     * Located routes that are all equal: the tables are kept by identity.
     */
    static final class EqualRoutes implements LocatedRoutes<Object> {
        int declared;

        @Override
        public Argument<Object> targetType() {
            return Argument.OBJECT_ARGUMENT;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Object> routes) {
            declared++;
            routes.GET("/items", LocatedRoutesTableTest::ok);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EqualRoutes;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
