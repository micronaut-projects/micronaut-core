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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route per service of the discovery client, following the services and their instances
 * (DR-09, LB-13).
 */
class DiscoveryLocatorTest extends GatewayTestBase {

    static final String SPEC = "DiscoveryLocatorTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of(
            "micronaut.gateway.discovery-locator.enabled", true,
            "micronaut.gateway.discovery-locator.include-pattern", "orders|billing|Mixed",
            "micronaut.gateway.discovery-locator.lower-case-service-id", true,
            "micronaut.gateway.refresh-interval", "200ms");
    }

    private TestDiscoveryClient discovery() {
        return gateway.getApplicationContext().getBean(TestDiscoveryClient.class);
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

    @Test
    void dr09AndLb13ServicesAndInstancesComeAndGo() throws Exception {
        TestDiscoveryClient discovery = discovery();
        discovery.services.put("orders", List.of(ServiceInstance.of("orders", upstream.getURI())));
        awaitStatus("/orders/inspect/x", 200);
        assertEquals("/inspect/x", get("/orders/inspect/x").inspected("path"));
        // a new service gets a route; one that the include pattern leaves out does not
        discovery.services.put("billing", List.of(ServiceInstance.of("billing", upstream.getURI())));
        discovery.services.put("secret", List.of(ServiceInstance.of("secret", upstream.getURI())));
        awaitStatus("/billing/inspect/x", 200);
        assertEquals(404, get("/secret/inspect/x").status());
        // the path of a service is its id in lower case
        discovery.services.put("Mixed", List.of(ServiceInstance.of("Mixed", upstream.getURI())));
        GatewayRouteRegistry registry = gateway.getApplicationContext().getBean(GatewayRouteRegistry.class);
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (registry.findRoute("discovery_Mixed").isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(List.of("/mixed/**"), registry.findRoute("discovery_Mixed").orElseThrow().getPaths());
        assertEquals("lb://Mixed", registry.findRoute("discovery_Mixed").orElseThrow().getUri().toString());
        // a new instance of a service is selected too
        EmbeddedServer second = Upstream.start(Map.of());
        try {
            discovery.services.put("orders", List.of(ServiceInstance.of("orders", upstream.getURI()), ServiceInstance.of("orders", second.getURI())));
            Set<String> hosts = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                RawHttp.Response response = get("/orders/inspect/x");
                assertEquals(200, response.status());
                hosts.add(response.inspected("h:host"));
            }
            assertEquals(2, hosts.size(), hosts.toString());
        } finally {
            second.getApplicationContext().close();
        }
        // a service that goes away loses its route
        discovery.services.remove("billing");
        awaitStatus("/billing/inspect/x", 404);
        assertTrue(gateway.getApplicationContext().getBean(GatewayRouteRegistry.class).findRoute("discovery_orders").isPresent());
    }

    /**
     * Services the test changes.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class TestDiscoveryClient implements DiscoveryClient {
        final Map<String, List<ServiceInstance>> services = new ConcurrentHashMap<>();

        @Override
        public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return Publishers.just(services.getOrDefault(serviceId, List.of()));
        }

        @Override
        public Publisher<List<String>> getServiceIds() {
            return Publishers.just(new ArrayList<>(services.keySet()));
        }

        @Override
        public String getDescription() {
            return "test";
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
