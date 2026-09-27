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
import io.micronaut.http.gateway.GatewayAttributes;
import io.micronaut.http.gateway.GatewayCircuitBreaker;
import io.micronaut.http.gateway.GatewayFilter;
import io.micronaut.http.gateway.GatewayRetry;
import io.micronaut.http.gateway.GatewayRoute;
import io.micronaut.http.gateway.GatewayRouteRegistry;
import io.micronaut.http.uri.UriRouteTemplate;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.CircuitState;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.builder.RequestCondition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The read-only gateway endpoint, {@code /gateway}: the gateway routes, with their predicates,
 * filters, upstream and order, the templates the router matches for them, and the states of
 * their circuit breakers. It is sensitive by default, like the {@code routes} endpoint. It has
 * no write operation: routes are added or changed in the code or the configuration only.
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
    private final Router router;
    private final CircuitBreakerRegistry circuitBreakers;

    /**
     * @param registry        The gateway routes
     * @param router          The router
     * @param circuitBreakers The circuit breakers
     */
    public GatewayEndpoint(GatewayRouteRegistry registry, Router router, CircuitBreakerRegistry circuitBreakers) {
        this.registry = registry;
        this.router = router;
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

    private Map<String, Object> describe(GatewayRoute route) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("id", route.getId());
        description.put("uri", route.getUri().toString());
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
            description.put("circuitBreaker", cb);
        }
        if (route.getResponseTimeout() != null) {
            description.put("responseTimeout", Objects.toString(route.getResponseTimeout()));
        }
        if (!route.getMetadata().isEmpty()) {
            Map<String, String> metadata = new LinkedHashMap<>();
            route.getMetadata().forEach((k, v) -> metadata.put(k, String.valueOf(v)));
            description.put("metadata", metadata);
        }
        TreeSet<String> templates = new TreeSet<>();
        router.uriRoutes()
            .filter(r -> route.getId().equals(r.getAttribute(GatewayAttributes.ROUTE_ID).orElse(null)))
            .map(GatewayEndpoint::template)
            .forEach(templates::add);
        description.put("templates", List.copyOf(templates));
        return description;
    }

    private static String template(UriRouteInfo<?, ?> route) {
        return route.getRouteTemplate() instanceof UriRouteTemplate uri ? uri.getUriMatchTemplate().toPathString() : route.getRouteTemplate().expression();
    }
}
