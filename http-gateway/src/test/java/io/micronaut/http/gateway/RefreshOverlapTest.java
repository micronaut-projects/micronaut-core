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
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A periodic refresh does not start while a slow locator still loads the last one, and a service
 * id that is not a path does not fail the routes of the discovery locator.
 */
class RefreshOverlapTest extends GatewayTestBase {

    static final String SPEC = "RefreshOverlapTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of("micronaut.gateway.refresh-interval", "100ms");
    }

    @Test
    void aSlowLocatorDoesNotPileUpPeriodicRefreshes() throws Exception {
        Thread.sleep(1500);
        int calls = gateway.getApplicationContext().getBean(StalledLocator.class).calls.get();
        // the refresh at startup, and the first periodic one: the others are skipped
        assertTrue(calls <= 2, "loads in flight: " + calls);
    }

    @Test
    void aServiceIdThatIsNotAPathIsSkipped() throws Exception {
        DiscoveryGatewayRouteLocator.Configuration configuration = new DiscoveryGatewayRouteLocator.Configuration();
        DiscoveryClient client = new DiscoveryClient() {
            @Override
            public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
                return Flux.empty();
            }

            @Override
            public Publisher<List<String>> getServiceIds() {
                return Mono.just(List.of("orders", "bad{id}", "a*b", "users"));
            }

            @Override
            public String getDescription() {
                return "test";
            }

            @Override
            public void close() {
            }
        };
        List<GatewayRoute> routes = new DiscoveryGatewayRouteLocator(client, configuration).getRoutes().toCompletableFuture().get();
        assertEquals(List.of("orders", "users"), routes.stream().map(r -> r.getPaths().get(0).substring(1, r.getPaths().get(0).length() - 3)).toList());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class StalledLocator implements GatewayRouteLocator {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<List<GatewayRoute>> getRoutes() {
            calls.incrementAndGet();
            // never answers
            return new CompletableFuture<>();
        }
    }
}
