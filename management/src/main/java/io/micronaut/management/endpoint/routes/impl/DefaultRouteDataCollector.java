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
package io.micronaut.management.endpoint.routes.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.management.endpoint.routes.RouteData;
import io.micronaut.management.endpoint.routes.RouteDataCollector;
import io.micronaut.management.endpoint.routes.RoutesEndpoint;
import io.micronaut.web.router.UriRoute;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A Reactive route data collector.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(beans = RoutesEndpoint.class)
public class DefaultRouteDataCollector implements RouteDataCollector<Map<String, Object>> {

    private final RouteData routeData;

    /**
     * @param routeData       The RouteData
     */
    public DefaultRouteDataCollector(RouteData routeData) {
        this.routeData = routeData;
    }

    @Override
    public Publisher<Map<String, Object>> getData(Stream<UriRoute> routes) {
        List<UriRoute> routeList = routes.collect(Collectors.toList());
        return Flux.fromIterable(routeList)
                .collectMap(this::getRouteKey, routeData::getData);
    }

    /**
     * @param route The URI route
     * @return The route key
     */
    protected String getRouteKey(UriRoute route) {
        String produces = route
            .getProduces()
            .stream()
            .map(MediaType::toString)
            .collect(Collectors.joining(" || "));

        return new StringBuilder("{[")
            .append(route.getUriMatchTemplate())
            .append("],method=[")
            .append(route.getHttpMethodName())
            .append("],produces=[")
            .append(produces)
            .append("]}")
            .toString();
    }
}
