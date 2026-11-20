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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The failover relays what the route prepared and keeps its limits, a client failure or a client
 * that goes away starts no fallback, the last response is relayed when the retries stop, and a
 * body longer than the replay limit is still sent again when no attempt read it.
 */
class ResilienceHardeningTest extends GatewayTestBase {

    static final String SPEC = "ResilienceHardeningTest";
    static final int DEAD_PORT = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("test.dead", "http://127.0.0.1:" + DEAD_PORT);
        p.put("micronaut.retry.circuit-breakers.fo-size.request-volume-threshold", 1);
        p.put("micronaut.retry.circuit-breakers.fo-size.failure-ratio", 0.1);
        p.put("micronaut.retry.circuit-breakers.fo-size.reset", "1m");
        p.put("micronaut.http.services.replaybig.urls", List.of("http://127.0.0.1:" + DEAD_PORT, "${test.scripted}"));
        return p;
    }

    @Test
    void aFailoverRelaysTheRequestTheRoutePrepared() throws IOException {
        RawHttp.Response response = get("/fo/original/path", "Authorization: Bearer secret");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/moved", response.inspected("path"));
        assertNull(response.inspected("h:authorization"), "the credentials the route removes reached the secondary");
        assertEquals("gw", response.inspected("h:x-added"));
    }

    @Test
    void aFailoverKeepsTheBodyLimitOfTheRouteWhileItsCircuitIsOpen() throws IOException {
        // the first request opens the circuit
        assertEquals(200, get("/fo-size/inspect/x").status());
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /fo-size/inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n"
                + "64\r\n" + "x".repeat(100) + "\r\n0\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals(413, response.status(), response.toString());
        }
    }

    @Test
    void aBodyOverTheLimitIsAnsweredWithoutTheFallback() throws IOException {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /size-fallback/inspect/x HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n"
                + "64\r\n" + "x".repeat(100) + "\r\n0\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals(413, response.status(), response.toString());
            assertNotEquals("fallback", response.text());
        }
    }

    @Test
    void aClientThatGoesAwayStartsNoFailover() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Void> received = new CompletableFuture<>();
        scripted.on("/hang-fo", (request, in, out, socket) -> {
            if (calls.incrementAndGet() == 1) {
                received.complete(null);
                while (in.read() >= 0) {
                    // until the gateway closes the connection
                }
                return false;
            }
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\nfailover");
            return true;
        });
        Socket client = new Socket("127.0.0.1", gateway.getPort());
        client.getOutputStream().write("GET /fo-abandoned/hang-fo HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
        received.get(10, TimeUnit.SECONDS);
        client.close();
        Thread.sleep(1000);
        assertEquals(1, calls.get(), "the failover ran for a client that went away");
    }

    @Test
    void theLastResponseIsRelayedWhenTheRetriesStop() throws IOException {
        scripted.on("/moved", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 302 Found\r\nLocation: /elsewhere\r\nX-Retry: yes\r\nContent-Length: 5\r\n\r\nmoved");
            return true;
        });
        RawHttp.Response response = get("/retry-budget/moved");
        assertEquals(302, response.status(), response.toString());
        assertEquals("/elsewhere", response.header("Location"));
        assertEquals("moved", response.text());
    }

    @Test
    void aBodyLongerThanTheReplayLimitIsSentAgainWhenNoAttemptReadIt() throws IOException {
        scripted.on("/length", (request, in, out, socket) -> {
            byte[] body = request.readBody(in);
            String text = String.valueOf(body.length);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: " + text.length() + "\r\n\r\n" + text);
            return true;
        });
        for (int i = 0; i < 4; i++) {
            RawHttp.Response response = send("POST", "/replay-big/length", "y".repeat(100), "Content-Type: text/plain");
            assertEquals(200, response.status(), response.toString());
            assertEquals("100", response.text());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final String dead;
        private final ProxyHandlerFactory proxies;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted, @Value("${test.dead}") String dead,
               ProxyHandlerFactory proxies) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.dead = dead;
            this.proxies = proxies;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            AsyncRequestHandler fallback = (request, pathVariables) -> CompletableFuture.completedFuture(
                HttpResponse.ok("fallback").contentType(MediaType.TEXT_PLAIN_TYPE));
            gateway.route("fo").path("/fo/**")
                .filters(GatewayFilters.setPath("/inspect/moved"), GatewayFilters.removeRequestHeader("Authorization"),
                    GatewayFilters.addRequestHeader("X-Added", "gw"))
                .circuitBreaker(GatewayCircuitBreaker.of("fo", proxies.to(upstream)))
                .uri(dead);
            gateway.route("fo-size").path("/fo-size/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestSize(10))
                .circuitBreaker(GatewayCircuitBreaker.of("fo-size", proxies.to(upstream)))
                .uri(dead);
            gateway.route("size-fallback").path("/size-fallback/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestSize(5))
                .circuitBreaker(GatewayCircuitBreaker.of("size-fallback", fallback))
                .uri(upstream);
            gateway.route("fo-abandoned").path("/fo-abandoned/**")
                .filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("fo-abandoned", proxies.to(scripted)))
                .uri(scripted);
            gateway.route("retry-budget").path("/retry-budget/**")
                .filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(3).withResponseHeader("X-Retry", "yes").withBackoff(Duration.ofMillis(50), 1, 0, Duration.ofMillis(1)))
                .uri(scripted);
            gateway.route("replay-big").path("/replay-big/**")
                .filters(GatewayFilters.stripPrefix(1))
                .retry(GatewayRetry.of(2).withMethods("POST").withMaxReplayBytes(8).withBackoff(Duration.ofMillis(1), 1))
                .uri("lb://replaybig");
        }
    }
}
