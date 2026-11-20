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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway routes added and deleted while the application runs, in memory, like Spring Cloud
 * Gateway's {@code InMemoryRouteDefinitionRepository}: a {@link GatewayRouteLocator} whose changes
 * apply on the next refresh. Enabled with {@code micronaut.gateway.route-repository.enabled}; the
 * gateway endpoint then deletes a route with {@code DELETE /gateway/{id}}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Singleton
@Requires(property = GatewayConfiguration.PREFIX + ".route-repository.enabled", value = StringUtils.TRUE)
public final class InMemoryGatewayRouteRepository implements GatewayRouteLocator {
    private final Map<String, GatewayRoute> routes = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<List<GatewayRoute>> getRoutes() {
        return CompletableFuture.completedFuture(new ArrayList<>(routes.values()));
    }

    /**
     * Add or replace a route, by its id.
     *
     * @param route The route
     */
    public void save(GatewayRoute route) {
        routes.put(Objects.requireNonNull(route, "route").getId(), route);
    }

    /**
     * @param id The id of a route
     * @return Whether the route was deleted: {@code false} if there is no such route
     */
    public boolean delete(String id) {
        return routes.remove(id) != null;
    }

    /**
     * Remove a route, for a delete that can be undone.
     *
     * @param id The id of a route
     * @return The removed route, or {@code null} if there is no such route
     */
    @Nullable GatewayRoute remove(String id) {
        return routes.remove(id);
    }

    /**
     * Put a removed route back, unless a route of its id was saved since.
     *
     * @param route The route
     */
    void restore(GatewayRoute route) {
        routes.putIfAbsent(route.getId(), route);
    }
}
