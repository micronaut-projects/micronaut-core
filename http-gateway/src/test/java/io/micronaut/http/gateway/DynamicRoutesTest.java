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
package io.micronaut.http.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.CircuitState;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes of route locators that change while the gateway runs (DR-06 to DR-08, DR-10 to DR-12,
 * WT-04).
 */
class DynamicRoutesTest extends GatewayTestBase {

    static final String SPEC = "DynamicRoutesTest";
    private final int managementPort = SocketUtils.findAvailableTcpPort();
    private final int deadPort = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.gateway.route-repository.enabled", true);
        p.put("endpoints.all.port", managementPort);
        p.put("endpoints.gateway.enabled", true);
        p.put("endpoints.gateway.sensitive", false);
        p.put("micronaut.server.idle-timeout", "5s");
        return p;
    }

    private TestLocator locator() {
        return gateway.getApplicationContext().getBean(TestLocator.class);
    }

    private GatewayRouteRegistry registry() {
        return gateway.getApplicationContext().getBean(GatewayRouteRegistry.class);
    }

    private int refresh() throws Exception {
        return registry().refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private GatewayRoute route(String id, String prefix, GatewayFilter... filters) {
        return GatewayRoute.builder(id).path("/" + prefix + "/**").filters(GatewayFilters.stripPrefix(1)).filters(filters)
            .uri(upstream.getURI()).build();
    }

    private RawHttp.Response management(String method, String path) throws IOException {
        return RawHttp.exchange(managementPort, method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" + (method.equals("GET") ? "" : "Content-Length: 0\r\n") + "\r\n");
    }

    @BeforeEach
    void reset() throws Exception {
        locator().routes.clear();
        locator().failure = null;
        refresh();
    }

    @Test
    void theRoutesOfALocatorAreRoutedAfterARefreshAndDoNotShadowTheOtherRoutes() throws Exception {
        assertEquals(404, get("/dyn/inspect/x").status());
        locator().routes.add(() -> route("dyn", "dyn"));
        assertEquals(1, refresh());
        assertEquals("/inspect/x", get("/dyn/inspect/x").inspected("path"));
        // the other routes
        assertEquals(200, get("/static/inspect/x").status());
        assertEquals(404, get("/nowhere").status());
        assertTrue(registry().findRoute("dyn").isPresent());
    }

    @Test
    void dr06AFailedRefreshKeepsTheLastRoutes() throws Exception {
        locator().routes.add(() -> route("dyn", "dyn"));
        refresh();
        locator().failure = new IllegalStateException("the store is down");
        assertThrows(ExecutionException.class, this::refresh);
        assertEquals(200, get("/dyn/inspect/x").status(), "the last routes stay");
        // an invalid route does not replace them either
        locator().failure = null;
        locator().routes.add(() -> GatewayRoute.builder("bad").path("/bad/**").uri("ftp://nope").build());
        assertThrows(ExecutionException.class, this::refresh);
        assertEquals(200, get("/dyn/inspect/x").status());
    }

    @Test
    void dr07ARequestInFlightCompletesOnTheRouteItWasMatchedWith() throws Exception {
        locator().routes.add(() -> route("dyn", "dyn"));
        refresh();
        CompletableFuture<RawHttp.Response> slow = CompletableFuture.supplyAsync(() -> {
            try {
                return get("/dyn/up/slow?ms=1000");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(300);
        locator().routes.clear();
        refresh();
        assertEquals(200, slow.get(10, TimeUnit.SECONDS).status());
        assertEquals(404, get("/dyn/inspect/x").status());
    }

    @Test
    void dr08TheRepositoryAddsAndDeletesRoutes() throws Exception {
        InMemoryGatewayRouteRepository repository = gateway.getApplicationContext().getBean(InMemoryGatewayRouteRepository.class);
        repository.save(route("repo", "repo"));
        refresh();
        assertEquals(200, get("/repo/inspect/x").status());
        RawHttp.Response deleted = management("DELETE", "/gateway/repo");
        assertEquals(200, deleted.status(), deleted.toString());
        refresh();
        assertEquals(404, get("/repo/inspect/x").status());
        assertEquals(404, management("DELETE", "/gateway/unknown").status());
    }

    @Test
    void dr10ARefreshEventAndTheEndpointRefreshTheRoutes() throws Exception {
        locator().routes.add(() -> route("event", "event"));
        gateway.getApplicationContext().getBean(ApplicationEventPublisher.class).publishEvent(new RefreshEvent());
        awaitStatus("/event/inspect/x", 200);
        locator().routes.add(() -> route("posted", "posted"));
        RawHttp.Response refreshed = management("POST", "/gateway/refresh");
        assertEquals(200, refreshed.status(), refreshed.toString());
        assertTrue(refreshed.text().contains("\"refreshed\":2"), refreshed.text());
        assertEquals(200, get("/posted/inspect/x").status());
    }

    @Test
    void dr11TheFiltersOfARouteChangeWithARefresh() throws Exception {
        locator().routes.add(() -> route("filtered", "filtered", GatewayFilters.addResponseHeader("X-Version", "1")));
        refresh();
        assertEquals("1", get("/filtered/inspect/x").header("X-Version"));
        locator().routes.clear();
        locator().routes.add(() -> route("filtered", "filtered", GatewayFilters.addResponseHeader("X-Version", "2")));
        refresh();
        assertEquals("2", get("/filtered/inspect/x").header("X-Version"));
    }

    @Test
    void to12TheTimeoutOfARouteChangesWithARefresh() throws Exception {
        locator().routes.add(() -> GatewayRoute.builder("timed").path("/timed/**").filters(GatewayFilters.stripPrefix(1))
            .responseTimeout(Duration.ofMillis(300)).uri(upstream.getURI()).build());
        refresh();
        assertEquals(504, get("/timed/up/slow?ms=1000").status());
        locator().routes.clear();
        locator().routes.add(() -> GatewayRoute.builder("timed").path("/timed/**").filters(GatewayFilters.stripPrefix(1))
            .responseTimeout(Duration.ofSeconds(5)).uri(upstream.getURI()).build());
        refresh();
        assertEquals(200, get("/timed/up/slow?ms=1000").status());
    }

    @Test
    void dr12TheStateOfACircuitBreakerSurvivesARefresh() throws Exception {
        Supplier<GatewayRoute> broken = () -> GatewayRoute.builder("broken").path("/broken/**")
            .circuitBreaker(GatewayCircuitBreaker.of("dynamic-broken")).uri(URI.create("http://127.0.0.1:" + deadPort)).build();
        locator().routes.add(broken);
        refresh();
        assertEquals(502, get("/broken/x").status());
        CircuitBreakerRegistry circuitBreakers = gateway.getApplicationContext().getBean(CircuitBreakerRegistry.class);
        assertEquals(CircuitState.OPEN, circuitBreakers.findState("dynamic-broken").orElseThrow());
        refresh();
        assertEquals(CircuitState.OPEN, circuitBreakers.findState("dynamic-broken").orElseThrow());
        assertEquals(503, get("/broken/x").status(), "still open after the refresh");
    }

    @Test
    void wt04TheWeightGroupsAreRecalculatedByARefresh() throws Exception {
        locator().routes.add(() -> GatewayRoute.builder("w-a").path("/w/**").weight("dynamic", 1)
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "a")).uri(upstream.getURI()).build());
        locator().routes.add(() -> GatewayRoute.builder("w-b").path("/w/**").weight("dynamic", 0)
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "b")).uri(upstream.getURI()).build());
        refresh();
        for (int i = 0; i < 10; i++) {
            assertEquals("a", get("/w/inspect/x").header("X-Route"));
        }
        locator().routes.clear();
        locator().routes.add(() -> GatewayRoute.builder("w-a").path("/w/**").weight("dynamic", 0)
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "a")).uri(upstream.getURI()).build());
        locator().routes.add(() -> GatewayRoute.builder("w-b").path("/w/**").weight("dynamic", 1)
            .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "b")).uri(upstream.getURI()).build());
        refresh();
        for (int i = 0; i < 10; i++) {
            assertEquals("b", get("/w/inspect/x").header("X-Route"));
        }
    }

    private void awaitStatus(String path, int status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        int last;
        do {
            last = get(path).status();
            if (last == status) {
                return;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertEquals(status, last, path);
    }

    /**
     * Routes the tests change.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class TestLocator implements GatewayRouteLocator {
        final List<Supplier<GatewayRoute>> routes = new CopyOnWriteArrayList<>();
        volatile RuntimeException failure;

        @Override
        public CompletionStage<List<GatewayRoute>> getRoutes() {
            RuntimeException f = failure;
            if (f != null) {
                return CompletableFuture.failedFuture(f);
            }
            return CompletableFuture.completedFuture(routes.stream().map(Supplier::get).toList());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class StaticRoutes implements GatewayRoutes {
        private final String upstream;

        StaticRoutes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("static").path("/static/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
        }
    }
}
