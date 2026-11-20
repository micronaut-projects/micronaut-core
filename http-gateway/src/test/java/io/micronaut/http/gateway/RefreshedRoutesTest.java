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
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The routes of the route locators are declared with the router like the other gateway routes,
 * after a refresh: exact templates and prefixes. A refresh whose routes cannot be declared fails
 * before it is published, and the last routes stay.
 */
class RefreshedRoutesTest extends GatewayTestBase {

    static final String SPEC = "RefreshedRoutesTest";

    @Override
    String specName() {
        return SPEC;
    }

    private Locator locator() {
        return gateway.getApplicationContext().getBean(Locator.class);
    }

    private int refresh() throws Exception {
        return gateway.getApplicationContext().getBean(GatewayRouteRegistry.class).refresh().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @BeforeEach
    void reset() throws Exception {
        locator().routes.clear();
        refresh();
    }

    @Test
    void theExactAndPrefixRoutesOfALocatorAreRoutedAfterARefresh() throws Exception {
        locator().routes.add(GatewayRoute.builder("exact").path("/located/{name}").filters(GatewayFilters.prefixPath("/inspect")).uri(upstream.getURI()).build());
        locator().routes.add(GatewayRoute.builder("prefix").path("/prefixed/**").filters(GatewayFilters.prefixPath("/inspect")).uri(upstream.getURI()).build());
        assertEquals(2, refresh());

        assertEquals("/inspect/located/x", get("/located/x").inspected("path"));
        assertEquals(404, get("/located/x/y").status(), "an exact template routes its own paths only");
        assertEquals("/inspect/prefixed", get("/prefixed").inspected("path"));
        assertEquals("/inspect/prefixed/a/b", get("/prefixed/a/b").inspected("path"));

        // the next refresh replaces them
        locator().routes.clear();
        assertEquals(0, refresh());
        assertEquals(404, get("/prefixed/a/b").status());
    }

    @Test
    void aRefreshWhoseRoutesCannotBeDeclaredFailsAndTheLastRoutesStay() throws Exception {
        locator().routes.add(GatewayRoute.builder("good").path("/good/**").filters(GatewayFilters.prefixPath("/inspect")).uri(upstream.getURI()).build());
        refresh();
        // a prefix with a variable named path cannot be declared: the refresh fails, and the
        // routes of the last refresh answer, rather than every request failing
        locator().routes.add(GatewayRoute.builder("bad").path("/bad/{path}/**").uri(upstream.getURI()).build());
        assertThrows(ExecutionException.class, this::refresh);
        assertEquals("/inspect/good/x", get("/good/x").inspected("path"));
        assertEquals(404, get("/bad/a/x").status());
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Locator implements GatewayRouteLocator {
        final List<GatewayRoute> routes = new CopyOnWriteArrayList<>();

        @Override
        public CompletionStage<List<GatewayRoute>> getRoutes() {
            return CompletableFuture.completedFuture(List.copyOf(routes));
        }
    }
}
