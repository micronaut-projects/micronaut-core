/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.endpoint.routes;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * <p>Exposes an {@link Endpoint} to display application routes.</p>
 *
 * @author James Kleeh
 * @see io.micronaut.http.annotation.HttpMethodMapping
 * @since 1.0
 */
@Endpoint("routes")
public class RoutesEndpoint {

    private final Router router;
    private final RouteDataCollector<Object> routeDataCollector;

    /**
     * @param router The {@link Router}
     * @param routeDataCollector The {@link RouteDataCollector}
     */
    public RoutesEndpoint(Router router, RouteDataCollector<Object> routeDataCollector) {
        this.router = router;
        this.routeDataCollector = routeDataCollector;
    }

    /**
     * @return The routes data representing the routes.
     */
    @Read
    @SingleResult
    public Object getRoutes() {
        Stream<UriRouteInfo<?, ?>> uriRoutes = router.uriRoutes()
                .sorted(Comparator.comparing((UriRouteInfo<?, ?> r) -> r.getUriMatchTemplate().toPathString()).thenComparing(UriRouteInfo::getHttpMethodName));
        return routeDataCollector.getData(uriRoutes);
    }
}
