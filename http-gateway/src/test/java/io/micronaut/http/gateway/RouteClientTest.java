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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client configuration of a route to a URI: {@code client: <name>} applies the settings of
 * {@code micronaut.http.services.<name>} to the upstream connections of the route.
 */
class RouteClientTest extends GatewayTestBase {

    @Override
    String specName() {
        return "RouteClientTest";
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.http.services.impatient.read-timeout", "500ms");
        String r = "micronaut.gateway.routes.";
        p.put(r + "named.uri", "${test.upstream}");
        p.put(r + "named.client", "impatient");
        p.put(r + "named.predicates", List.of("Path=/rc/named/**"));
        p.put(r + "named.filters", List.of("StripPrefix=2"));
        p.put(r + "plain.uri", "${test.upstream}");
        p.put(r + "plain.predicates", List.of("Path=/rc/plain/**"));
        p.put(r + "plain.filters", List.of("StripPrefix=2"));
        return p;
    }

    @Test
    void theClientConfigurationOfTheRouteApplies() throws IOException {
        long start = System.nanoTime();
        RawHttp.Response response = get("/rc/named/up/slow?ms=2000");
        assertEquals(504, response.status(), response.toString());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1800, "the read timeout of the client did not apply");
        // the request still goes to the URI of the route
        assertEquals(200, get("/rc/named/inspect/x").status());
        // a route without a client uses the default client configuration, read timeout 20s
        assertEquals(200, get("/rc/plain/up/slow?ms=1000").status());
    }

    @Test
    void anUnknownClientFailsTheStartup() {
        Map<String, Object> p = new HashMap<>();
        p.put("micronaut.gateway.routes.bad.uri", "${test.upstream}");
        p.put("micronaut.gateway.routes.bad.client", "nope");
        p.put("micronaut.gateway.routes.bad.predicates", List.of("Path=/bad/**"));
        Exception failure = assertThrows(Exception.class, () -> startGateway(specName(), upstream, p).getApplicationContext().close());
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("micronaut.http.services.nope"), root.getMessage());
    }

    @Test
    void aLoadBalancedRouteUsesTheClientOfItsService() {
        assertThrows(IllegalArgumentException.class, () -> GatewayRoute.builder("lb").uri("lb://orders").client("other").build());
        assertEquals("other", GatewayRoute.builder("u").uri("https://up").client("other").build().toBuilder().build().getClient());
    }
}
