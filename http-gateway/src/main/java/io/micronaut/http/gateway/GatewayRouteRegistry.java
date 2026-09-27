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

import io.micronaut.core.annotation.Experimental;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The gateway routes the router has, e.g. for the gateway endpoint: those of the
 * {@link GatewayRoutes} beans and of the configuration, once the router is built.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface GatewayRouteRegistry {

    /**
     * @return The routes, in the order they were declared, with the default filters
     */
    List<GatewayRoute> getRoutes();

    /**
     * @param id The id of a route
     * @return The route
     */
    default Optional<GatewayRoute> findRoute(String id) {
        return getRoutes().stream().filter(route -> route.getId().equals(id)).findFirst();
    }

    /**
     * Load the routes of the {@link GatewayRouteLocator route locators} again, see
     * {@link GatewayRouteLocator}.
     *
     * @return The stage of the number of routes of the locators, failed if the refresh failed
     * and the last routes stay
     * @since 5.3.0
     */
    default CompletionStage<Integer> refresh() {
        return CompletableFuture.completedFuture(0);
    }

    /**
     * Delete a route of the {@link InMemoryGatewayRouteRepository route repository}, and
     * refresh the routes.
     *
     * @param id The id of the route
     * @return Whether the route was deleted: {@code false} if the repository has no such route
     * or there is no repository
     * @since 5.3.0
     */
    default boolean deleteRoute(String id) {
        return false;
    }
}
