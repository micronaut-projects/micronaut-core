/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.management.endpoint.routes;

import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Read;
import org.particleframework.web.router.Router;
import org.particleframework.http.annotation.HttpMethodMapping;
import org.reactivestreams.Publisher;

/**
 * <p>Exposes an {@link Endpoint} to display application routes</p>
 *
 * @see HttpMethodMapping
 *
 * @author James Kleeh
 * @since 1.0
 */
@Endpoint("routes")
public class RoutesEndpoint {

    private final Router router;
    private final RouteDataCollector routeDataCollector;

    public RoutesEndpoint(Router router, RouteDataCollector routeDataCollector) {
        this.router = router;
        this.routeDataCollector = routeDataCollector;
    }

    @Read
    public Publisher getRoutes() {
        return routeDataCollector.getData(router.uriRoutes());
    }
}
