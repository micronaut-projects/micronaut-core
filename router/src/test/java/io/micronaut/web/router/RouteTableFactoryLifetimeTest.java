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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tables of {@link io.micronaut.web.router.builder.LocatedRoutes} instances: built once per
 * instance, outside of the map that keeps them, and kept no longer than the instance.
 */
class RouteTableFactoryLifetimeTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void aTableIsBuiltOncePerInstanceUnderConcurrentFirstUse() throws Exception {
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TestLocatedRoutes<Object> slow = TestLocatedRoutes.of(located -> {
            building.countDown();
            await(release);
            located.GET("/items", (request, pathVariables) -> HttpResponse.ok());
        });
        TestLocatedRoutes<Object> other = TestLocatedRoutes.of(located -> located.GET("/other", (request, pathVariables) -> HttpResponse.ok()));
        ExecutorService executor = Executors.newFixedThreadPool(9);
        try {
            List<Future<RouteTable>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> tables.table(slow)));
            }
            assertTrue(building.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            // the table of another instance does not wait for the one being built
            assertNotNull(executor.submit(() -> tables.table(other)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            release.countDown();
            RouteTable first = results.get(0).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (Future<RouteTable> result : results) {
                assertSame(first, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            assertEquals(1, slow.declared.get());
            assertEquals(1, other.declared.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void theRoutesOfAnInstanceMayBuildTheTablesOfOtherInstances() {
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        List<TestLocatedRoutes<Object>> inner = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            inner.add(TestLocatedRoutes.of(located -> located.GET("/lines", (request, pathVariables) -> HttpResponse.ok())));
        }
        List<RouteTable> innerTables = new ArrayList<>();
        TestLocatedRoutes<Object> outer = TestLocatedRoutes.of(located -> {
            // e.g. routes that check the tables of the routes they locate while they are declared
            for (TestLocatedRoutes<Object> routes : inner) {
                innerTables.add(tables.table(routes));
            }
            located.GET("/items", (request, pathVariables) -> HttpResponse.ok());
        });

        RouteTable outerTable = tables.table(outer);
        assertNotNull(((DefaultRouteTable) outerTable).routes().findClosest(HttpRequest.GET("/items"), null));
        for (int i = 0; i < inner.size(); i++) {
            assertSame(innerTables.get(i), tables.table(inner.get(i)));
            assertEquals(1, inner.get(i).declared.get());
        }
        assertSame(outerTable, tables.table(outer));
        assertEquals(1, outer.declared.get());
    }

    @Test
    void aTableThatFailsToBuildIsBuiltAgain() {
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        TestLocatedRoutes<Object> failing = TestLocatedRoutes.of(located -> {
            throw new IllegalStateException("not yet");
        });
        for (int i = 1; i <= 2; i++) {
            try {
                tables.table(failing);
            } catch (IllegalStateException e) {
                assertEquals("not yet", e.getMessage());
            }
            assertEquals(i, failing.declared.get());
        }
    }

    /**
     * Best effort: the collector is asked to run until the instance is collected.
     */
    @Test
    void anUnreferencedInstanceIsCollectable() throws InterruptedException {
        RouteTableFactory tables = new RouteTableFactory(null, ConversionService.SHARED);
        WeakReference<?> reference = buildAndForget(tables);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (reference.get() != null && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(reference.get(), "the table does not keep its routes reachable");
        // the factory keeps serving
        TestLocatedRoutes<Object> routes = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        assertSame(tables.table(routes), tables.table(routes));
    }

    private static WeakReference<?> buildAndForget(RouteTableFactory tables) {
        TestLocatedRoutes<Object> routes = TestLocatedRoutes.of(located -> located.GET("/items", (request, pathVariables) -> HttpResponse.ok()));
        assertNotNull(tables.table(routes));
        return new WeakReference<>(routes);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
