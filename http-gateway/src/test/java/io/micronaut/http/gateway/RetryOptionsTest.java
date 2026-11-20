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
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.web.router.builder.RouteFilterSpec;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backoff, limits and conditions of retries (RY-11, RY-12, RY-14).
 */
class RetryOptionsTest extends GatewayTestBase {

    static final String SPEC = "RetryOptionsTest";
    static final int DEAD_PORT = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        // the read timeout of the client, not a response timeout: its timer runs on the event loop
        return Map.of("micronaut.server.idle-timeout", "5s", "micronaut.http.client.read-timeout", "400ms");
    }

    private AtomicInteger always503(String path) {
        AtomicInteger calls = new AtomicInteger();
        scripted.on(path, (request, in, out, socket) -> {
            calls.incrementAndGet();
            ScriptedUpstream.write(out, "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n");
            return true;
        });
        return calls;
    }

    @Test
    void ry11TheBackoffGrowsAndTheTotalDelayIsCapped() throws IOException {
        AtomicInteger calls = always503("/backoff");
        long start = System.nanoTime();
        assertEquals(503, get("/backoff/backoff").status());
        long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertEquals(4, calls.get());
        // 100 + 200 + 400 ms
        assertTrue(elapsed >= 650, "elapsed " + elapsed);

        AtomicInteger capped = always503("/capped");
        assertEquals(503, get("/capped/capped").status());
        assertTrue(capped.get() < 4, "the total delay stops the retries: " + capped.get());

        assertThrows(IllegalArgumentException.class, () -> GatewayRetry.of(1).withBackoff(Duration.ofMillis(1), 1, 2, null));
        assertThrows(IllegalArgumentException.class, () -> GatewayRetry.of(1).withBackoff(Duration.ofMillis(-1), 1));
        assertThrows(IllegalArgumentException.class, () -> GatewayRetry.of(1).withBackoff(Duration.ofMillis(1), 1, 0, Duration.ofMillis(-1)));
    }

    @Test
    void ry12OnlyTheConfiguredFailuresAreRetried() throws IOException {
        AtomicInteger slowCalls = new AtomicInteger();
        scripted.on("/slow-once", (request, in, out, socket) -> {
            if (slowCalls.incrementAndGet() == 1) {
                Thread.sleep(1000);
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return true;
        });
        RawHttp.Response retried = get("/timeouts-only/slow-once");
        assertEquals(200, retried.status(), retried.toString());
        assertEquals("ok", retried.text());
        assertEquals(2, slowCalls.get());

        AtomicInteger closedCalls = new AtomicInteger();
        scripted.on("/closed", (request, in, out, socket) -> {
            closedCalls.incrementAndGet();
            // closed before the headers: not a timeout
            return false;
        });
        assertEquals(502, get("/timeouts-only/closed").status());
        assertEquals(1, closedCalls.get());
    }

    @Test
    void ry12AResponseHeaderRetriesTheRequest() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        scripted.on("/header", (request, in, out, socket) -> {
            if (calls.incrementAndGet() == 1) {
                ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nX-Retry: yes\r\nContent-Length: 4\r\n\r\nbusy");
            } else {
                ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            }
            return true;
        });
        assertEquals("ok", get("/header-retry/header").text());
        assertEquals(2, calls.get());
    }

    @Test
    void ry12TheTransportErrorIsExposedToTheFiltersAndErrorRoutes() throws IOException {
        RawHttp.Response response = get("/exposed/x");
        assertEquals(502, response.status(), response.toString());
        assertEquals("UnprocessedRequestException", response.header("X-Failure"));
    }

    @Test
    void ry14ABackupInstanceIsTriedLast() throws IOException {
        java.util.List<io.micronaut.discovery.ServiceInstance> instances = java.util.List.of(
            io.micronaut.discovery.ServiceInstance.of("backed", java.net.URI.create("http://127.0.0.1:" + DEAD_PORT)),
            io.micronaut.discovery.ServiceInstance.of("backed", java.net.URI.create("http://127.0.0.1:" + SocketUtils.findAvailableTcpPort())),
            io.micronaut.discovery.ServiceInstance.builder("backed", java.net.URI.create(scripted.uri().toString())).metadata(Map.of("backup", "true")).build());
        io.micronaut.http.client.loadbalance.ServiceInstanceListRoundRobinLoadBalancer balancer =
            new io.micronaut.http.client.loadbalance.ServiceInstanceListRoundRobinLoadBalancer(new io.micronaut.discovery.ServiceInstanceList() {
                @Override
                public String getID() {
                    return "backed";
                }

                @Override
                public java.util.List<io.micronaut.discovery.ServiceInstance> getInstances() {
                    return instances;
                }
            });
        // backup instances apply with a load balancer strategy
        balancer.setStrategy(io.micronaut.http.client.loadbalance.LoadBalancerStrategy.of(io.micronaut.http.client.loadbalance.LoadBalancerStrategy.ROUND_ROBIN));
        gateway.getApplicationContext().getBean(ProxyHandlerFactory.class).loadBalancer("backed", balancer);
        AtomicInteger calls = new AtomicInteger();
        scripted.on("/backed", (request, in, out, socket) -> {
            calls.incrementAndGet();
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\nbackup");
            return true;
        });
        // both primaries refuse the connection: the backup answers
        RawHttp.Response response = get("/backed/backed");
        assertEquals(200, response.status(), response.toString());
        assertEquals("backup", response.text());
        assertEquals(1, calls.get());
    }

    @Test
    void ry14TheNumberOfTriesIsTheRetriesPlusOne() throws IOException {
        AtomicInteger calls = always503("/tries");
        assertEquals(503, get("/tries/tries").status());
        assertEquals(3, calls.get());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String scripted;

        Routes(@Value("${test.scripted}") String scripted) {
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("backoff").path("/backoff/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(3).withStatuses(503).withBackoff(Duration.ofMillis(100), 2)).uri(scripted);
            gateway.route("capped").path("/capped/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(3).withStatuses(503).withBackoff(Duration.ofMillis(100), 2, 0, Duration.ofMillis(250))).uri(scripted);
            gateway.route("timeouts-only").path("/timeouts-only/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(2).withExceptions(ReadTimeoutException.class).withBackoff(Duration.ofMillis(1), 1)).uri(scripted);
            gateway.route("header-retry").path("/header-retry/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(2).withResponseHeader("X-Retry", "yes").withBackoff(Duration.ofMillis(1), 1)).uri(scripted);
            gateway.route("backed").path("/backed/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(2).withBackoff(Duration.ofMillis(1), 1)).uri("lb://backed");
            gateway.route("tries").path("/tries/**").filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(2).withStatuses(503).withBackoff(Duration.ofMillis(1), 1)).uri(scripted);
            gateway.route("exposed").path("/exposed/**").filters(new GatewayFilter() {
                @Override
                public void configure(RouteFilterSpec<?> route) {
                    route.after((request, response) -> GatewayRequestContext.find(request).flatMap(GatewayRequestContext::failure)
                        .ifPresent(failure -> response.header("X-Failure", failure.getClass().getSimpleName())));
                }
            }).uri("http://127.0.0.1:" + DEAD_PORT);
        }
    }
}
