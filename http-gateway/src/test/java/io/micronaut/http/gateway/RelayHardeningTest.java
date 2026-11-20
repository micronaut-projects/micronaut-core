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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a relay does not pass on or does not do: the trailers that would be read as headers, the
 * transport metadata of HTTP/2, a malformed {@code Host}, the bytes of a response whose status
 * has no content, a fallback for a failure of the gateway, and a circuit breaker failure for a
 * client that went away.
 */
class RelayHardeningTest extends GatewayTestBase {

    static final String SPEC = "RelayHardeningTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of(
            "micronaut.server.reject-unsupported-http-versions", true,
            // one failure opens the circuit
            "micronaut.retry.circuit-breakers.abandoned.request-volume-threshold", 1,
            "micronaut.retry.circuit-breakers.abandoned.failure-ratio", 0.1,
            "micronaut.retry.circuit-breakers.abandoned.reset", "1m"
        );
    }

    @Test
    void theTrailersThatWouldBeReadAsHeadersAreNotRelayedInEitherDirection() throws Exception {
        scripted.on("/trailers", (request, in, out, socket) -> {
            List<String> trailers = new ArrayList<>();
            request.readBody(in, trailers);
            String seen = String.join(";", trailers).toLowerCase(java.util.Locale.ROOT);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n"
                + Integer.toHexString(seen.length()) + "\r\n" + seen + "\r\n0\r\ngrpc-status: 0\r\nX-Forwarded-For: 6.6.6.6\r\nSet-Cookie: a=b\r\n\r\n");
            return true;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /raw/trailers HTTP/1.1\r\nHost: localhost\r\nTE: trailers\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n"
                + "4\r\nbody\r\n0\r\nx-checksum: 1\r\nX-Forwarded-For: 1.2.3.4\r\nProxy-Authorization: Basic eDp5\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals(200, response.status(), response.toString());
            assertEquals("x-checksum: 1", response.text(), "the trailers the upstream received");
            assertEquals("0", response.header("trailer:grpc-status"), response.toString());
            assertNull(response.header("trailer:X-Forwarded-For"), response.toString());
            assertNull(response.header("trailer:Set-Cookie"), response.toString());
        }
    }

    @Test
    void theTransportMetadataOfHttp2IsNotRelayed() throws IOException {
        RawHttp.Response response = get("/up/inspect/x", "x-http2-stream-id: 5", "X-Http2-Scheme: https");
        assertEquals(200, response.status(), response.toString());
        assertNull(response.inspected("h:x-http2-stream-id"), response.toString());
        assertNull(response.inspected("h:x-http2-scheme"), response.toString());
    }

    @Test
    void aMalformedHostIsRefused() throws IOException {
        for (String host : List.of("[::1", "localhost:abc", "localhost:99999", "[localhost]")) {
            RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /up/inspect/x HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n");
            assertEquals(400, response.status(), host + ": " + response);
        }
        RawHttp.Response valid = RawHttp.exchange(gateway.getPort(), "GET /up/inspect/x HTTP/1.1\r\nHost: [::1]:" + gateway.getPort() + "\r\nConnection: close\r\n\r\n");
        assertEquals(200, valid.status(), valid.toString());
    }

    @Test
    void aHostThatAFilterSetFromAPathVariableIsValidatedToo() throws IOException {
        assertEquals(400, get("/host/a%2Fb/inspect/x").status());
        assertEquals(400, get("/host/a@b/inspect/x").status());
        RawHttp.Response valid = get("/host/tenant.example/inspect/x");
        assertEquals(200, valid.status(), valid.toString());
        assertEquals("tenant.example", valid.inspected("h:host"));
    }

    @Test
    void aStatusWithoutContentDoesNotSendNorHoldTheBytesOfTheUpstream() throws Exception {
        CompletableFuture<Boolean> upstreamClosed = new CompletableFuture<>();
        scripted.on("/stream-forever", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/plain\r\n\r\n");
            try {
                for (int i = 0; i < 200; i++) {
                    ScriptedUpstream.write(out, "5\r\nchunk\r\n");
                    Thread.sleep(50);
                }
                upstreamClosed.complete(false);
            } catch (IOException e) {
                // the gateway closed the stream
                upstreamClosed.complete(true);
            }
            return false;
        });
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("GET /no-content/stream-forever HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RawHttp.Response response = http.read(false);
            assertEquals(204, response.status(), response.toString());
            assertEquals("", response.text());
            // the connection serves the next request: the stream of the upstream does not occupy it
            http.send("GET /up/inspect/next HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RawHttp.Response next = http.read(false);
            assertEquals(200, next.status(), next.toString());
        }
        assertTrue(upstreamClosed.get(10, TimeUnit.SECONDS), "the stream of the upstream was not closed");
    }

    @Test
    void theFallbackDoesNotAnswerAFailureOfAFilterAfterTheUpstreamAnswered() throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response response = get("/cb-filter-fails/inspect/x");
        assertEquals(500, response.status(), response.toString());
        assertNotEquals("fallback", response.text());
        assertEquals(before + 1, counters().get("inspect"), "the upstream answered once");
    }

    @Test
    void aClientThatGoesAwayIsNotAFailureOfTheCircuit() throws Exception {
        CompletableFuture<Void> received = new CompletableFuture<>();
        scripted.on("/hang", (request, in, out, socket) -> {
            received.complete(null);
            // never answers: the client goes away first
            while (in.read() >= 0) {
                // until the gateway closes the connection
            }
            return false;
        });
        Socket client = new Socket("127.0.0.1", gateway.getPort());
        client.getOutputStream().write("GET /cb-abandoned/hang HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
        received.get(10, TimeUnit.SECONDS);
        client.close();
        Thread.sleep(500);
        scripted.on("/fine", (request, in, out, socket) -> {
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nfine");
            return true;
        });
        RawHttp.Response response = get("/cb-abandoned/fine");
        assertEquals(200, response.status(), "the circuit opened for the client that went away: " + response);
        assertEquals("fine", response.text());
    }

    @Test
    void anUpgradeOfAnUnsupportedVersionIsRefusedLikeAnyOtherRequest() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "GET /raw/ws HTTP/3.0\r\nHost: localhost\r\nUpgrade: websocket\r\n"
            + "Connection: Upgrade\r\nSec-WebSocket-Key: " + WebSockets.KEY + "\r\nSec-WebSocket-Version: 13\r\n\r\n");
        assertEquals(505, response.status(), response.toString());
    }

    @Test
    void anUpstreamConnectionOfAnUpgradeIsClosedWhenTheClientGoesAwayDuringTheHandshake() throws Exception {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        CompletableFuture<Void> received = new CompletableFuture<>();
        scripted.on("/ws-late", (request, in, out, socket) -> {
            received.complete(null);
            Thread.sleep(500);
            try {
                ScriptedUpstream.write(out, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                    + WebSockets.accept(request.header("sec-websocket-key")) + "\r\n\r\n");
                while (in.read() >= 0) {
                    // until the gateway closes the tunnel
                }
            } catch (IOException e) {
                // closed
            }
            closed.complete(null);
            return false;
        });
        Socket client = new Socket("127.0.0.1", gateway.getPort());
        client.getOutputStream().write(WebSockets.handshake("/raw/ws-late", new String[0]).replace("Host: localhost", "Host: gw.example").getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
        received.get(10, TimeUnit.SECONDS);
        client.close();
        closed.get(15, TimeUnit.SECONDS);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;
        private final AsyncRequestHandler fallback;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted) {
            this.upstream = upstream;
            this.scripted = scripted;
            this.fallback = (request, pathVariables) -> CompletableFuture.completedFuture(HttpResponse.ok("fallback").contentType(MediaType.TEXT_PLAIN_TYPE));
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("up").path("/up/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
            gateway.route("raw").path("/raw/**").filters(GatewayFilters.stripPrefix(1)).uri(scripted);
            gateway.route("host").path("/host/{h}/**").filters(GatewayFilters.stripPrefix(2), GatewayFilters.setRequestHostHeader("{h}")).uri(upstream);
            gateway.route("no-content").path("/no-content/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.setStatus(204)).uri(scripted);
            gateway.route("cb-filter-fails").path("/cb-filter-fails/**")
                .filters(GatewayFilters.stripPrefix(1), new GatewayFilter() {
                    @Override
                    public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
                        throw new IllegalStateException("the response filter failed");
                    }
                })
                .circuitBreaker(GatewayCircuitBreaker.of("filter-fails", fallback))
                .uri(upstream);
            gateway.route("cb-abandoned").path("/cb-abandoned/**").filters(GatewayFilters.stripPrefix(1))
                .circuitBreaker(GatewayCircuitBreaker.of("abandoned", fallback))
                .uri(scripted);
        }
    }
}
