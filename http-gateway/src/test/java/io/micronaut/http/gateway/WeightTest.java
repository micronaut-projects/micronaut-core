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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weight groups: routes that split the traffic, a canary override and sticky clients (WT-01,
 * WT-02, WT-03, WT-05).
 */
class WeightTest extends GatewayTestBase {

    static final String SPEC = "WeightTest";

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("micronaut.gateway.weight-groups.canary.override-header", "X-Canary");
        properties.put("micronaut.gateway.weight-groups.sticky.sticky-header", "X-User");
        // a group from the configuration
        properties.put("micronaut.gateway.routes.yaml-a.uri", "${test.upstream}");
        properties.put("micronaut.gateway.routes.yaml-a.predicates", List.of("Path=/yaml/**", "Weight=yaml, 3"));
        properties.put("micronaut.gateway.routes.yaml-a.filters", List.of("StripPrefix=1", "AddResponseHeader=X-Route, yaml-a"));
        properties.put("micronaut.gateway.routes.yaml-b.uri", "${test.upstream}");
        properties.put("micronaut.gateway.routes.yaml-b.predicates", List.of("Path=/yaml/**", "Weight=yaml, 1"));
        properties.put("micronaut.gateway.routes.yaml-b.filters", List.of("StripPrefix=1", "AddResponseHeader=X-Route, yaml-b"));
        return properties;
    }

    private Map<String, Integer> split(String path, int requests, String... headers) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < requests; i++) {
            RawHttp.Response response = get(path, headers);
            assertEquals(200, response.status(), response.toString());
            counts.merge(response.header("X-Route"), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void wt01TheRoutesOfAGroupSplitTheTrafficByWeight() throws IOException {
        Map<String, Integer> counts = split("/weighted/inspect/x", 200);
        int blue = counts.getOrDefault("blue", 0);
        int green = counts.getOrDefault("green", 0);
        assertEquals(200, blue + green, counts.toString());
        assertTrue(blue > 120 && green > 10, counts.toString());
    }

    @Test
    void aGroupChoosesOnlyAmongTheRoutesARequestMatches() {
        GatewayRoute get = GatewayRoute.builder("get").uri("http://a").path("/w/**")
            .where(io.micronaut.web.router.builder.RequestCondition.method("GET")).weight("g", 1).build();
        GatewayRoute post = GatewayRoute.builder("post").uri("http://b").path("/w/**")
            .where(io.micronaut.web.router.builder.RequestCondition.method("POST")).weight("g", 1).build();
        GatewayRoute other = GatewayRoute.builder("other").uri("http://c").path("/elsewhere/**").weight("g", 1).build();
        WeightGroup group = WeightGroup.of(List.of(get, post, other), Map.of(), io.micronaut.web.router.RouteConditionContext.fallback()).get("g");
        for (int i = 0; i < 200; i++) {
            assertEquals("get", group.chosen(io.micronaut.http.HttpRequest.GET("/w/x")));
            assertEquals("post", group.chosen(io.micronaut.http.HttpRequest.POST("/w/x", "")));
        }
        assertNull(group.chosen(io.micronaut.http.HttpRequest.PUT("/w/x", "")), "no route of the group matches");
    }

    @Test
    void wt02TheChoiceIsDeterministicForARandomValueAndThreadSafe() throws Exception {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("a", 2);
        weights.put("b", 8);
        weights.put("zero", 0);
        WeightGroup group = new WeightGroup("g", weights, null);
        assertEquals("a", group.choose(0.0));
        assertEquals("a", group.choose(0.199));
        assertEquals("b", group.choose(0.2));
        assertEquals("b", group.choose(0.999));
        assertNull(new WeightGroup("none", Map.of("x", 0), null).choose(0.5));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                results.add(executor.submit(() -> {
                    for (int i = 0; i < 10_000; i++) {
                        double r = (i % 1000) / 1000.0;
                        if (!group.choose(r).equals(r < 0.2 ? "a" : "b")) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void wt03AGroupFromTheConfigurationNeverAnswers404() throws IOException {
        Map<String, Integer> counts = split("/yaml/inspect/x", 60);
        assertEquals(60, counts.getOrDefault("yaml-a", 0) + counts.getOrDefault("yaml-b", 0), counts.toString());
        assertTrue(counts.getOrDefault("yaml-a", 0) > counts.getOrDefault("yaml-b", 0), counts.toString());
    }

    @Test
    void wt05AHeaderForcesARouteOfTheGroup() throws IOException {
        assertEquals(Map.of("stable", 20), split("/canary/inspect/x", 20));
        assertEquals(Map.of("next", 20), split("/canary/inspect/x", 20, "X-Canary: next"));
        // an unknown route is ignored
        assertEquals(Map.of("stable", 5), split("/canary/inspect/x", 5, "X-Canary: nope"));
    }

    @Test
    void aStickyHeaderKeepsAClientOnItsRoute() throws IOException {
        Set<String> routes = new HashSet<>();
        for (int user = 0; user < 20; user++) {
            Map<String, Integer> counts = split("/sticky/inspect/x", 5, "X-User: user-" + user);
            assertEquals(1, counts.size(), "user-" + user + " moved: " + counts);
            routes.addAll(counts.keySet());
        }
        assertEquals(Set.of("left", "right"), routes, "the users spread over the routes");
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
            gateway.route("blue").path("/weighted/**").weight("colors", 8)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "blue")).uri(upstream);
            gateway.route("green").path("/weighted/**").weight("colors", 2)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "green")).uri(upstream);
            gateway.route("stable").path("/canary/**").weight("canary", 10)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "stable")).uri(upstream);
            gateway.route("next").path("/canary/**").weight("canary", 0)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "next")).uri(upstream);
            gateway.route("left").path("/sticky/**").weight("sticky", 1)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "left")).uri(upstream);
            gateway.route("right").path("/sticky/**").weight("sticky", 1)
                .filters(GatewayFilters.stripPrefix(1), GatewayFilters.addResponseHeader("X-Route", "right")).uri(upstream);
        }
    }
}
