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
import io.micronaut.http.HttpRequest;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A rate limit from the default filters of the configuration applies to every route, with a
 * bucket per route (RL-08), and a named key resolver bean.
 */
class RateLimitDefaultFilterTest extends GatewayTestBase {

    static final String SPEC = "RateLimitDefaultFilterTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        return Map.of(
            "micronaut.gateway.default-filters", List.of("RequestRateLimiter=1, 60, 60, tenant"),
            "micronaut.gateway.routes.one.uri", "${test.upstream}",
            "micronaut.gateway.routes.one.predicates", List.of("Path=/one/**"),
            "micronaut.gateway.routes.one.filters", List.of("StripPrefix=1"),
            "micronaut.gateway.routes.two.uri", "${test.upstream}",
            "micronaut.gateway.routes.two.predicates", List.of("Path=/two/**"),
            "micronaut.gateway.routes.two.filters", List.of("StripPrefix=1"));
    }

    @Test
    void rl08TheDefaultFiltersLimitEveryRoute() throws IOException {
        RawHttp.Response first = get("/one/inspect/x", "X-Tenant: t");
        assertEquals(200, first.status(), first.toString());
        assertEquals("0", first.header("X-RateLimit-Remaining"));
        assertEquals(429, get("/one/inspect/x", "X-Tenant: t").status());
        // the other route has its own bucket
        assertEquals(200, get("/two/inspect/x", "X-Tenant: t").status());
        assertEquals(429, get("/two/inspect/x", "X-Tenant: t").status());
    }

    @Singleton
    @Named("tenant")
    @Requires(property = "spec.name", value = SPEC)
    static class TenantKeyResolver implements KeyResolver {
        @Override
        public String resolve(HttpRequest<?> request) {
            return request.getHeaders().get("X-Tenant");
        }
    }
}
