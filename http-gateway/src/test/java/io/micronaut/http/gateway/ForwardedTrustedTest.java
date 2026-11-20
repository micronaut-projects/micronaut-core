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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The forwarded headers when the client is a trusted proxy: the gateway appends itself to them.
 */
class ForwardedTrustedTest extends GatewayTestBase {

    static final String SPEC = "ForwardedTrustedTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.gateway.forwarded.trusted-proxies", List.of("127.0.0.0/8", "::1"));
    }

    @Test
    void fw02TheChainOfATrustedProxyIsAppendedTo() throws IOException {
        RawHttp.Response response = get("/inspect/x", "X-Forwarded-For: 203.0.113.7, 10.0.0.1",
            "X-Forwarded-Proto: https", "X-Forwarded-Host: shop.example", "X-Forwarded-Port: 443");
        assertEquals("203.0.113.7, 10.0.0.1, 127.0.0.1", response.inspected("h:x-forwarded-for"), response.toString());
        assertEquals("https", response.inspected("h:x-forwarded-proto"));
        assertEquals("shop.example", response.inspected("h:x-forwarded-host"));
        assertEquals("443", response.inspected("h:x-forwarded-port"));
    }

    @Test
    void fw05SeveralElementsAndHeadersOfForwardedAreReadWithCaseInsensitiveKeys() throws IOException {
        RawHttp.Response response = get("/inspect/x", "Forwarded: For=203.0.113.7;Proto=https;Host=shop.example, for=10.0.0.1",
            "Forwarded: for=\"[2001:db8::1]:4711\"");
        String[] hops = response.inspected("h:x-forwarded-for").split(", ");
        assertEquals(4, hops.length, response.toString());
        assertEquals("203.0.113.7", hops[0]);
        assertEquals("10.0.0.1", hops[1]);
        assertEquals(true, hops[2].contains("2001:db8::1"), hops[2]);
        assertEquals("127.0.0.1", hops[3]);
        assertEquals("https", response.inspected("h:x-forwarded-proto"));
        assertEquals("shop.example", response.inspected("h:x-forwarded-host"));
        assertEquals("For=203.0.113.7;Proto=https;Host=shop.example, for=10.0.0.1, for=\"[2001:db8::1]:4711\", for=127.0.0.1;proto=http;host=\"localhost:" + gateway.getPort() + "\"",
            response.inspected("h:forwarded"));
    }

    @Test
    void fw07AnInvalidSchemeOfATrustedProxyIsIgnored() throws IOException {
        RawHttp.Response response = get("/inspect/x", "X-Forwarded-Proto: javascript", "X-Forwarded-Port: 1; x");
        assertEquals("http", response.inspected("h:x-forwarded-proto"), response.toString());
        assertEquals(String.valueOf(gateway.getPort()), response.inspected("h:x-forwarded-port"));
    }

    @Test
    void fw09ThePortOfATrustedHopIsKept() throws IOException {
        assertEquals("8443", get("/inspect/x", "X-Forwarded-Port: 8443", "X-Forwarded-Proto: https").inspected("h:x-forwarded-port"));
    }

    @Test
    void pt06TheStrippedPrefixIsAppendedToThePrefixOfATrustedProxy() throws IOException {
        assertEquals("/edge/api", get("/api/inspect/x", "X-Forwarded-Prefix: /edge").inspected("h:x-forwarded-prefix"));
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
            gateway.route("inspect").path("/inspect/**").uri(upstream);
            gateway.route("api").path("/api/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
        }
    }
}
