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
package io.micronaut.http.gateway.endpoint;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.gateway.GatewayCircuitBreaker;
import io.micronaut.http.gateway.GatewayFilter;
import io.micronaut.http.gateway.GatewayRetry;
import io.micronaut.http.gateway.GatewayRoute;
import io.micronaut.http.gateway.GatewayRouteRegistry;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.management.endpoint.annotation.Sensitive;
import io.micronaut.management.endpoint.annotation.Write;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.CircuitState;
import io.micronaut.web.router.builder.RequestCondition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

/**
 * The gateway endpoint, {@code /gateway}: the gateway routes, with their predicates, filters,
 * upstream and order, the templates of the routes they
 * are registered as, and the states of their
 * circuit breakers. It is sensitive by default, like the {@code routes} endpoint, and its
 * write operations stay sensitive when its reads are not, unless
 * {@code endpoints.gateway.write-sensitive} is {@code false}. Its write
 * operations are {@code POST /gateway/refresh}, which loads the routes of the route locators
 * again, see {@link io.micronaut.http.gateway.GatewayRouteLocator}, and
 * {@code DELETE /gateway/{id}}, which deletes a route of the route repository, see
 * {@link io.micronaut.http.gateway.InMemoryGatewayRouteRepository}; the other routes are
 * changed in the code or the configuration only.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Endpoint(id = GatewayEndpoint.NAME, defaultSensitive = true)
@Requires(beans = GatewayRouteRegistry.class)
public class GatewayEndpoint {

    /**
     * The id of the endpoint.
     */
    public static final String NAME = "gateway";

    private final GatewayRouteRegistry registry;
    private final CircuitBreakerRegistry circuitBreakers;

    /**
     * @param registry        The gateway routes
     * @param circuitBreakers The circuit breakers
     */
    public GatewayEndpoint(GatewayRouteRegistry registry, CircuitBreakerRegistry circuitBreakers) {
        this.registry = registry;
        this.circuitBreakers = circuitBreakers;
    }

    /**
     * @return The gateway routes
     */
    @Read
    public Map<String, Object> getRoutes() {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (GatewayRoute route : registry.getRoutes()) {
            routes.add(describe(route));
        }
        return Map.of("routes", routes);
    }

    /**
     * @param id The id of a route
     * @return The route, or {@code null} for a {@code 404}
     */
    @Read
    public @Nullable Map<String, Object> getRoute(@Selector String id) {
        return registry.findRoute(id).map(this::describe).orElse(null);
    }

    /**
     * Load the routes of the route locators again: {@code POST /gateway/refresh}.
     *
     * @param action The action, {@code refresh}
     * @return The number of routes of the locators; a failed refresh keeps the last routes and
     * answers with an error
     */
    @Write
    @Sensitive(property = "write-sensitive", defaultValue = true)
    public CompletionStage<Map<String, Object>> post(@Selector String action) {
        if (!"refresh".equals(action)) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No gateway action " + action);
        }
        return registry.refresh().thenApply(count -> Map.of("refreshed", count));
    }

    /**
     * Delete a route of the route repository: {@code DELETE /gateway/{id}}.
     *
     * @param id The id of the route
     * @return The deleted route id, or {@code null} for a {@code 404}, once the routes without
     * it are published
     */
    @Delete
    @Sensitive(property = "write-sensitive", defaultValue = true)
    public CompletionStage<@Nullable Map<String, Object>> delete(@Selector String id) {
        return registry.deleteRoute(id).thenApply(deleted -> deleted ? Map.of("deleted", id) : null);
    }

    private static String redact(java.net.URI uri) {
        String userInfo = uri.getRawUserInfo();
        if (userInfo == null) {
            return uri.toString();
        }
        String s = uri.toString();
        int at = s.indexOf(userInfo + "@");
        return at < 0 ? s : s.substring(0, at) + "***@" + s.substring(at + userInfo.length() + 1);
    }

    private Map<String, Object> describe(GatewayRoute route) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("id", route.getId());
        // the user info of a URI may hold credentials
        description.put("uri", redact(route.getUri()));
        description.put("order", route.getOrder());
        description.put("paths", route.getPaths());
        List<String> conditions = new ArrayList<>(route.getConditions().size());
        for (RequestCondition condition : route.getConditions()) {
            conditions.add(condition.toString());
        }
        description.put("predicates", conditions);
        List<String> filters = new ArrayList<>(route.getFilters().size());
        for (GatewayFilter filter : route.getFilters()) {
            filters.add(filter.toString());
        }
        description.put("filters", filters);
        GatewayRetry retry = route.getRetry();
        if (retry != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("retries", retry.retries());
            r.put("methods", new TreeSet<>(retry.methods()));
            r.put("statuses", new TreeSet<>(retry.statuses()));
            r.put("delay", retry.delay().toString());
            description.put("retry", r);
        }
        GatewayCircuitBreaker circuitBreaker = route.getCircuitBreaker();
        if (circuitBreaker != null) {
            Map<String, Object> cb = new LinkedHashMap<>();
            cb.put("name", circuitBreaker.name());
            cb.put("state", circuitBreakers.findState(circuitBreaker.name()).orElse(CircuitState.CLOSED).name());
            cb.put("fallback", circuitBreaker.fallback() != null);
            cb.put("failureStatuses", circuitBreaker.failureStatuses().isEmpty() ? "5xx" : new TreeSet<>(circuitBreaker.failureStatuses()));
            cb.put("fallbackStatuses", new TreeSet<>(circuitBreaker.fallbackStatuses()));
            circuitBreakers.findSnapshot(circuitBreaker.name()).ifPresent(snapshot -> {
                Map<String, Object> counters = new LinkedHashMap<>();
                counters.put("requestVolumeThreshold", snapshot.requestVolumeThreshold());
                counters.put("calls", snapshot.calls());
                counters.put("failures", snapshot.failures());
                counters.put("trials", snapshot.trials());
                counters.put("successes", snapshot.successes());
                counters.put("opened", snapshot.openedCount());
                if (snapshot.since() != null) {
                    counters.put("since", String.valueOf(snapshot.since()));
                }
                cb.put("counters", counters);
            });
            description.put("circuitBreaker", cb);
        }
        if (route.getResponseTimeout() != null) {
            description.put("responseTimeout", Objects.toString(route.getResponseTimeout()));
        }
        if (route.getWeight() != null) {
            description.put("weight", Map.of("group", route.getWeight().group(), "weight", route.getWeight().weight()));
        }
        if (!route.getMetadata().isEmpty()) {
            Map<String, String> metadata = new LinkedHashMap<>();
            route.getMetadata().forEach((k, v) -> metadata.put(k, String.valueOf(v)));
            description.put("metadata", metadata);
        }
        description.put("templates", List.copyOf(new TreeSet<>(route.getRouteTemplates())));
        return description;
    }
}
