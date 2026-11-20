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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.RequestConditions;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * The routes of a weight group and the choice among them, once per request: every route of the
 * group sees the same choice, so that exactly one of those that match wins. The choice is made
 * among the routes whose paths and other conditions match the request: a route the request could
 * not take is never chosen in place of one it could.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class WeightGroup {
    private final String name;
    private final List<String> routeIds;
    /**
     * The upper bound of the range of each route in [0, 1), cumulative; a route of weight 0 has
     * an empty range.
     */
    private final double[] upperBounds;
    private final int[] weights;
    /**
     * Whether a request matches the paths and the other conditions of each route, in the order of
     * {@link #routeIds}.
     */
    private final List<Predicate<HttpRequest<?>>> matchers;
    @Nullable
    private final String overrideHeader;
    @Nullable
    private final String stickyHeader;

    WeightGroup(String name, Map<String, Integer> weights, @Nullable GatewayWeightGroupConfiguration configuration) {
        this(name, weights, Map.of(), configuration);
    }

    WeightGroup(String name, Map<String, Integer> weights, Map<String, Predicate<HttpRequest<?>>> matchers,
                @Nullable GatewayWeightGroupConfiguration configuration) {
        this.name = name;
        this.routeIds = new ArrayList<>(weights.size());
        List<Integer> values = new ArrayList<>(weights.size());
        long total = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            routeIds.add(entry.getKey());
            values.add(entry.getValue());
            total += entry.getValue();
        }
        this.weights = values.stream().mapToInt(Integer::intValue).toArray();
        List<Predicate<HttpRequest<?>>> byRoute = new ArrayList<>(routeIds.size());
        for (String id : routeIds) {
            byRoute.add(matchers.getOrDefault(id, request -> true));
        }
        this.matchers = List.copyOf(byRoute);
        this.upperBounds = new double[routeIds.size()];
        long cumulative = 0;
        for (int i = 0; i < routeIds.size(); i++) {
            cumulative += values.get(i);
            upperBounds[i] = total == 0 ? 0 : (double) cumulative / total;
        }
        this.overrideHeader = configuration == null ? null : configuration.getOverrideHeader();
        this.stickyHeader = configuration == null ? null : configuration.getStickyHeader();
    }

    /**
     * @param routes The routes
     * @param configurations The settings of the groups, by name
     * @return The groups of the weighted routes, by name
     */
    static Map<String, WeightGroup> of(Iterable<GatewayRoute> routes, Map<String, GatewayWeightGroupConfiguration> configurations,
                                       RouteConditionContext context) {
        Map<String, Map<String, Integer>> members = new LinkedHashMap<>();
        Map<String, Predicate<HttpRequest<?>>> matchers = new LinkedHashMap<>();
        for (GatewayRoute route : routes) {
            GatewayWeight weight = route.getWeight();
            if (weight != null) {
                members.computeIfAbsent(weight.group(), g -> new LinkedHashMap<>()).put(route.getId(), weight.weight());
                matchers.put(route.getId(), matcher(route, context));
            }
        }
        Map<String, WeightGroup> groups = new LinkedHashMap<>();
        members.forEach((group, weights) -> groups.put(group, new WeightGroup(group, weights, matchers, configurations.get(group))));
        return groups;
    }

    /**
     * @param route   A route of a group
     * @param context What the server resolves for the conditions
     * @return Whether a request matches a path and every other condition of the route; a body
     * condition is met here, the server evaluates it once it read the body
     */
    private static Predicate<HttpRequest<?>> matcher(GatewayRoute route, RouteConditionContext context) {
        List<GatewayPathPattern> patterns = new ArrayList<>();
        for (String path : route.getPaths()) {
            patterns.add(GatewayPathPattern.parse(path));
        }
        List<RequestCondition> conditions = route.getConditions();
        return request -> {
            if (!patterns.isEmpty()) {
                String rawPath = request.getUri().getRawPath();
                boolean any = false;
                for (GatewayPathPattern pattern : patterns) {
                    if (pattern.rawVariables(rawPath) != null) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    return false;
                }
            }
            for (RequestCondition condition : conditions) {
                if (!RequestConditions.matches(condition, request, context)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * @return The name of the group
     */
    String name() {
        return name;
    }

    /**
     * The route of a random value: the route whose range holds it. Deterministic, and safe to
     * call from any thread.
     *
     * @param random A value in [0, 1)
     * @return The id of the route, or {@code null} if every route has weight 0
     */
    @Nullable String choose(double random) {
        for (int i = 0; i < upperBounds.length; i++) {
            if (random < upperBounds[i]) {
                return routeIds.get(i);
            }
        }
        return null;
    }

    /**
     * The route of a request, chosen once: by the override header, by a hash of the sticky
     * header, or at random.
     *
     * @param request The request
     * @return The id of the route, or {@code null} if none is chosen
     */
    @Nullable String chosen(HttpRequest<?> request) {
        GatewayRequestContext context = GatewayRequestContext.forFilters(request);
        Object cached = context.state(this, Object.class);
        if (cached != null) {
            return cached instanceof String id ? id : null;
        }
        String id = choose(request);
        // the routes of the group are asked in turn: they must see the same choice
        context.state(this, id == null ? Boolean.FALSE : id);
        return id;
    }

    private @Nullable String choose(HttpRequest<?> request) {
        boolean[] eligible = new boolean[routeIds.size()];
        boolean all = true;
        for (int i = 0; i < eligible.length; i++) {
            eligible[i] = matchers.get(i).test(request);
            all &= eligible[i];
        }
        if (overrideHeader != null) {
            String forced = request.getHeaders().get(overrideHeader);
            int index = forced == null ? -1 : routeIds.indexOf(forced.strip());
            if (index >= 0 && eligible[index]) {
                return routeIds.get(index);
            }
        }
        double random = ThreadLocalRandom.current().nextDouble();
        if (stickyHeader != null) {
            String key = request.getHeaders().get(stickyHeader);
            if (key != null) {
                random = unitHash(key);
            }
        }
        return all ? choose(random) : choose(random, eligible);
    }

    /**
     * The route of a random value among the routes a request matches, by their weights.
     *
     * @param random   A value in [0, 1)
     * @param eligible Whether the request matches each route
     * @return The id of the route, or {@code null} if no route it matches has a weight
     */
    @Nullable String choose(double random, boolean[] eligible) {
        long total = 0;
        for (int i = 0; i < weights.length; i++) {
            if (eligible[i]) {
                total += weights[i];
            }
        }
        if (total == 0) {
            return null;
        }
        double target = random * total;
        long cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            if (eligible[i]) {
                cumulative += weights[i];
                if (target < cumulative) {
                    return routeIds.get(i);
                }
            }
        }
        return null;
    }

    /**
     * @param key A key
     * @return A value in [0, 1) of the key, stable across restarts
     */
    static double unitHash(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
