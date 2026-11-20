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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The CORS policy of gateway routes, also of dynamic routes after a refresh (SEC-27, DR-11).
 */
class CorsTest extends GatewayTestBase {

    static final String SPEC = "CorsTest";

    @Override
    String specName() {
        return SPEC;
    }

    /**
     * The upstream listens on localhost and gets the relayed Origin with X-Forwarded-Host: its
     * CORS filter forbids a foreign origin for localhost, also behind forwarding headers. A real
     * upstream is not on the loopback interface of the client.
     */
    @Override
    Map<String, Object> upstreamProperties() {
        return Map.of("micronaut.server.cors.localhost-pass-through", true);
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.idle-timeout", "5s");
        p.put("micronaut.gateway.routes.yaml-cors.uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.yaml-cors.predicates", List.of("Path=/yaml-cors/**"));
        p.put("micronaut.gateway.routes.yaml-cors.filters", List.of("StripPrefix=1", "Cors=https://yaml.example"));
        return p;
    }

    private RawHttp.Response preflight(String path, String origin) throws IOException {
        return RawHttp.exchange(gateway.getPort(), ("OPTIONS " + path + " HTTP/1.1\r\nHost: gw.example\r\nOrigin: " + origin
            + "\r\nAccess-Control-Request-Method: GET\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII), false);
    }

    /**
     * A GET with an origin, to a host other than localhost: the CORS filter forbids other
     * origins for localhost, against drive-by-localhost attacks.
     */
    private RawHttp.Response originGet(String path, String origin) throws IOException {
        return RawHttp.exchange(gateway.getPort(), ("GET " + path + " HTTP/1.1\r\nHost: gw.example\r\nOrigin: " + origin
            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII), false);
    }

    @Test
    void sec27APreflightIsAnsweredAtTheGatewayAndAForbiddenOriginIs403() throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response preflight = preflight("/cors/inspect/x", "https://app.example");
        assertEquals(200, preflight.status(), preflight.toString());
        assertEquals("https://app.example", preflight.header("Access-Control-Allow-Origin"));
        assertEquals(before, counters().get("inspect"), "the preflight reached the upstream");
        RawHttp.Response allowed = originGet("/cors/inspect/x", "https://app.example");
        assertEquals(200, allowed.status());
        assertEquals("https://app.example", allowed.header("Access-Control-Allow-Origin"));
        // a forbidden origin: its preflight is 403, and its requests get no Access-Control-Allow-Origin
        assertEquals(403, preflight("/cors/inspect/x", "https://evil.example").status());
        assertNull(originGet("/cors/inspect/x", "https://evil.example").header("Access-Control-Allow-Origin"));
        // from the configuration
        assertEquals("https://yaml.example", originGet("/yaml-cors/inspect/x", "https://yaml.example").header("Access-Control-Allow-Origin"));
        assertEquals(403, preflight("/yaml-cors/inspect/x", "https://app.example").status());
        // a route without a policy is left alone
        RawHttp.Response other = originGet("/plain/inspect/x", "https://evil.example");
        assertEquals(200, other.status());
        assertNull(other.header("Access-Control-Allow-Origin"));
    }

    @Test
    void dr11ThePolicyOfADynamicRouteChangesWithARefresh() throws Exception {
        Locator locator = gateway.getApplicationContext().getBean(Locator.class);
        GatewayRouteRegistry registry = gateway.getApplicationContext().getBean(GatewayRouteRegistry.class);
        locator.origins.clear();
        locator.origins.add("https://one.example");
        registry.refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals("https://one.example", originGet("/dynamic-cors/inspect/x", "https://one.example").header("Access-Control-Allow-Origin"));
        assertEquals(403, preflight("/dynamic-cors/inspect/x", "https://two.example").status());
        locator.origins.clear();
        locator.origins.add("https://two.example");
        registry.refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(403, preflight("/dynamic-cors/inspect/x", "https://one.example").status());
        RawHttp.Response preflight = preflight("/dynamic-cors/inspect/x", "https://two.example");
        assertEquals(200, preflight.status(), preflight.toString());
        assertEquals("https://two.example", preflight.header("Access-Control-Allow-Origin"));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Locator implements GatewayRouteLocator {
        final List<String> origins = new CopyOnWriteArrayList<>(List.of("https://one.example"));
        private final String upstream;

        Locator(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public CompletionStage<List<GatewayRoute>> getRoutes() {
            return CompletableFuture.completedFuture(List.of(GatewayRoute.builder("dynamic-cors").path("/dynamic-cors/**")
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.cors(List.copyOf(origins), List.of(), List.of(), List.of(), false, -1))
                .uri(upstream).build()));
        }
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
            gateway.route("cors").path("/cors/**").filters(GatewayFilters.stripPrefix(1),
                GatewayFilters.cors(List.of("https://app.example"), List.of("GET", "POST"), List.of(), List.of(), false, 600)).uri(upstream);
            gateway.route("plain").path("/plain/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
        }
    }
}
