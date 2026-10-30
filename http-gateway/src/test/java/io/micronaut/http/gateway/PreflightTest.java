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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The CORS filter of the server answers the preflight of a gateway route, an exact template or a
 * prefix, with the global CORS configuration or the policy of the route: the preflight never
 * reaches the upstream.
 */
class PreflightTest extends GatewayTestBase {

    static final String SPEC = "PreflightTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.server.cors.enabled", true);
        p.put("micronaut.server.cors.configurations.app.allowed-origins", List.of("https://app.example"));
        return p;
    }

    private RawHttp.Response preflight(String path, String origin) throws IOException {
        return RawHttp.exchange(gateway.getPort(), ("OPTIONS " + path + " HTTP/1.1\r\nHost: gw.example\r\nOrigin: " + origin
            + "\r\nAccess-Control-Request-Method: PUT\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII), false);
    }

    @Test
    void thePreflightOfAnExactTemplateIsAnsweredByTheGlobalConfiguration() throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response preflight = preflight("/exact-cors/1", "https://app.example");
        assertEquals(200, preflight.status(), preflight.toString());
        assertEquals("https://app.example", preflight.header("Access-Control-Allow-Origin"));
        assertEquals(before, counters().get("inspect"), "the preflight reached the upstream");
    }

    @Test
    void thePreflightOfAPrefixWithAPolicyOfItsOwnIsAnsweredByThePolicy() throws IOException {
        int before = counters().get("inspect");
        RawHttp.Response preflight = preflight("/own-cors/a/b", "https://own.example");
        assertEquals(200, preflight.status(), preflight.toString());
        assertEquals("https://own.example", preflight.header("Access-Control-Allow-Origin"));
        assertEquals(before, counters().get("inspect"), "the preflight reached the upstream");
    }

    @Test
    void thePreflightOfADisallowedOriginIsRefusedAndNeverRelayed() throws IOException {
        for (String path : List.of("/exact-cors/1", "/prefix-cors/a/b")) {
            int before = counters().get("inspect");
            RawHttp.Response preflight = preflight(path, "https://evil.example");
            assertEquals(403, preflight.status(), path + ": " + preflight);
            assertEquals(before, counters().get("inspect"), path + ": the preflight reached the upstream");
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
            gateway.route("exact-cors").path("/exact-cors/{id}").filters(GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("prefix-cors").path("/prefix-cors/**").filters(GatewayFilters.prefixPath("/inspect")).uri(upstream);
            gateway.route("own-cors").path("/own-cors/**")
                .filters(GatewayFilters.cors(List.of("https://own.example"), List.of(), List.of(), List.of(), false, -1), GatewayFilters.prefixPath("/inspect"))
                .uri(upstream);
        }
    }
}
