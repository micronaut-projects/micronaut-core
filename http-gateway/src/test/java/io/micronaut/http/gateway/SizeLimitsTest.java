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
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The size limits of requests and responses (SZ-01 to SZ-08).
 */
class SizeLimitsTest extends GatewayTestBase {

    static final String SPEC = "SizeLimitsTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of(
            "micronaut.server.idle-timeout", "5s",
            "micronaut.server.netty.max-initial-line-length", 1024);
    }

    private RawHttp.Response chunked(String path, String... parts) throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n");
            for (String part : parts) {
                http.send(Integer.toHexString(part.length()) + "\r\n" + part + "\r\n");
                Thread.sleep(100);
            }
            http.send("0\r\n\r\n");
            return http.read(false);
        }
    }

    @Test
    void sz01ABodyWhoseLengthIsOverTheLimitIs413BeforeTheUpstream() throws IOException {
        int before = counters().get("upload");
        RawHttp.Response response = send("POST", "/size/up/upload", "x".repeat(20), "Content-Type: text/plain");
        assertEquals(413, response.status(), response.toString());
        assertTrue(response.header("errorMessage").contains("permissible limit is 10 bytes"), response.toString());
        assertEquals(before, counters().get("upload"), "the upstream was called");
        RawHttp.Response within = send("POST", "/size/up/upload", "x".repeat(10), "Content-Type: text/plain");
        assertEquals(200, within.status(), within.toString());
        assertEquals("POST " + Upstream.digest("x".repeat(10).getBytes(StandardCharsets.UTF_8)), within.text());
    }

    @Test
    void sz02AChunkedBodyOverTheLimitIs413WhileNoResponseWasRelayed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        scripted.on("/sz02", (request, in, out, socket) -> {
            calls.incrementAndGet();
            request.readBody(in);
            ScriptedUpstream.write(out, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
            return false;
        });
        RawHttp.Response response = chunked("/size-raw/sz02", "12345678", "12345678");
        assertEquals(413, response.status(), response.toString());
        RawHttp.Response within = chunked("/size-raw/sz02", "12345", "12345");
        assertEquals(200, within.status(), within.toString());
    }

    @Test
    void sz03HeadersLargerThanTheLimitAre431() throws IOException {
        RawHttp.Response response = get("/header-size/inspect/x", "X-Big: " + "v".repeat(300));
        assertEquals(431, response.status(), response.toString());
        assertTrue(response.header("errorMessage").contains("x-big") || response.header("errorMessage").contains("X-Big"), response.toString());
        // a cookie counts too
        RawHttp.Response cookie = get("/header-size/inspect/x", "Cookie: session=" + "c".repeat(300));
        assertEquals(431, cookie.status(), cookie.toString());
        assertEquals(200, get("/header-size/inspect/x", "X-Small: v").status());
    }

    @Test
    void sz04AUrlLongerThanTheInitialLineOfTheServerIsRejected() throws IOException {
        RawHttp.Response response = get("/inspect/" + "a".repeat(2000));
        // the server rejects the request line before any route: 413, where Envoy answers 431 and nginx 414
        assertEquals(413, response.status(), response.toString());
    }

    @Test
    void sz05MoreHeadersThanTheCountAre431() throws IOException {
        RawHttp.Response response = get("/header-count/inspect/x", "A: 1", "B: 2", "C: 3", "D: 4", "E: 5", "F: 6");
        assertEquals(431, response.status(), response.toString());
        assertEquals(200, get("/header-count/inspect/x").status());
    }

    @Test
    void sz06ALargeResponseIsStreamedWithoutABufferLimit() throws IOException {
        // the gateway never buffers a response: there is no response limit to hit
        RawHttp.Response response = get("/size/up/stream?size=5000000");
        assertEquals(200, response.status(), response.toString());
        assertEquals(5_000_000, response.body().length);
    }

    @Test
    void sz07AHugeMethodIsRejectedBeforeAnyRoute() throws IOException {
        RawHttp.Response response = RawHttp.exchange(gateway.getPort(), "X".repeat(2000) + " /inspect/x HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(response.status() >= 400 && response.status() < 500, response.toString());
    }

    @Test
    void sz08AStreamedBodyWithoutALimitIsNotBuffered() throws Exception {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        String[] parts = new String[64];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = "p".repeat(32 * 1024);
            expected.writeBytes(parts[i].getBytes(StandardCharsets.UTF_8));
        }
        RawHttp.Response response = chunked("/unlimited/up/upload", parts);
        assertEquals(200, response.status(), response.toString());
        assertEquals("POST " + Upstream.digest(expected.toByteArray()), response.text());
        assertNotNull(response.header("X-Kept"));
        assertEquals("false", response.header("X-Kept"));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;
        private final String scripted;

        Routes(@Value("${test.upstream}") String upstream, @Value("${test.scripted}") String scripted) {
            this.upstream = upstream;
            this.scripted = scripted;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            GatewayFilter kept = new GatewayFilter() {
                @Override
                public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) {
                    response.header("X-Kept", String.valueOf(exchange.request().getAttribute(GatewayAttributes.BODY_KEPT_FOR_REPLAY).isPresent()));
                }
            };
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("size").path("/size/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestSize(10)).uri(upstream);
            gateway.route("size-raw").path("/size-raw/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestSize(10)).uri(scripted);
            gateway.route("header-size").path("/header-size/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestHeaderSize(200, 0)).uri(upstream);
            gateway.route("header-count").path("/header-count/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.requestHeaderSize(100_000, 5)).uri(upstream);
            gateway.route("unlimited").path("/unlimited/**").filters(GatewayFilters.stripPrefix(1), kept).uri(upstream);
        }
    }
}
