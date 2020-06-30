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

import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import io.reactivex.Single;

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
    private final RouteDataCollector routeDataCollector;

    /**
     * @param router The {@link Router}
     * @param routeDataCollector The {@link RouteDataCollector}
     */
    public RoutesEndpoint(Router router, RouteDataCollector routeDataCollector) {
        this.router = router;
        this.routeDataCollector = routeDataCollector;
    }

    /**
     * @return The routes as a {@link Single}
     */
    @Read
    public Single getRoutes() {
        Stream<UriRoute> uriRoutes = router.uriRoutes()
                .sorted(Comparator
                        .comparing((UriRoute r) -> r.getUriMatchTemplate().toPathString())
                        .thenComparing((UriRoute r) -> r.getHttpMethodName()));
        return Single.fromPublisher(routeDataCollector.getData(uriRoutes));
    }
}
