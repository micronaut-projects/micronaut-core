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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The routes of a route locator under the context path of the server.
 */
class LocatorContextPathTest extends GatewayTestBase {

    static final String SPEC = "LocatorContextPathTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of(
            "micronaut.server.context-path", "/app",
            "micronaut.gateway.route-repository.enabled", true
        );
    }

    @Test
    void aRouteOfALocatorIsRoutedUnderTheContextPath() throws Exception {
        InMemoryGatewayRouteRepository repository = gateway.getApplicationContext().getBean(InMemoryGatewayRouteRepository.class);
        // the path filters see the path of the request, with the context path
        repository.save(GatewayRoute.builder("located").path("/located/**").filters(GatewayFilters.stripPrefix(2)).uri(upstream.getURI()).build());
        gateway.getApplicationContext().getBean(GatewayRouteRegistry.class).refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
        RawHttp.Response response = get("/app/located/inspect/x");
        assertEquals(200, response.status(), response.toString());
        assertEquals("/inspect/x", response.inspected("path"));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class StaticRoutes implements GatewayRoutes {
        private final String upstream;

        StaticRoutes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("static").path("/static/**").filters(GatewayFilters.stripPrefix(1)).uri(upstream);
        }
    }
}
