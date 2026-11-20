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
import io.micronaut.http.MutableHttpRequest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The filters of a route read the body, which is still relayed; empty and oversized bodies
 * (RB-17).
 */
class CacheRequestBodyTest extends GatewayTestBase {

    static final String SPEC = "CacheRequestBodyTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.server.idle-timeout", "5s");
    }

    private RawHttp.Response chunked(String... parts) throws Exception {
        try (RawHttp http = new RawHttp(gateway.getPort())) {
            http.send("POST /cached/inspect/x HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n");
            for (String part : parts) {
                http.send(Integer.toHexString(part.length()) + "\r\n" + part + "\r\n");
                Thread.sleep(100);
            }
            http.send("0\r\n\r\n");
            return http.read(false);
        }
    }

    @Test
    void rb17AFilterReadsTheBodyThatIsStillRelayed() throws Exception {
        RawHttp.Response response = chunked("hello ", "world");
        assertEquals(200, response.status(), response.toString());
        assertEquals("hello world", response.inspected("body"));
        assertEquals("11", response.inspected("h:x-cached-length"));
    }

    @Test
    void rb17AnEmptyBodyIsCachedEmpty() throws Exception {
        RawHttp.Response response = send("POST", "/cached/inspect/x", "", "Content-Type: text/plain");
        assertEquals(200, response.status(), response.toString());
        assertEquals("0", response.inspected("h:x-cached-length"));
    }

    @Test
    void rb17ABodyOverTheLimitIs413() throws Exception {
        assertEquals(413, send("POST", "/cached/inspect/x", "x".repeat(100), "Content-Type: text/plain").status());
        assertEquals(413, chunked("x".repeat(40), "x".repeat(40)).status());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;

        Routes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("cached").path("/cached/**").filters(GatewayFilters.stripPrefix(1), GatewayFilters.cacheRequestBody(64), new GatewayFilter() {
                @Override
                public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) {
                    byte[] cached = GatewayRequestContext.find(exchange.request()).flatMap(GatewayRequestContext::cachedRequestBody).orElse(null);
                    request.header("X-Cached-Length", cached == null ? "none" : String.valueOf(new String(cached, StandardCharsets.UTF_8).length()));
                }
            }).uri(upstream);
        }
    }
}
