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

/**
 * Declares gateway routes, see {@link GatewayRoutes}.
 *
 * <p>The path patterns of a route, {@link GatewayRoute.Builder#path(String...)}, are URI
 * templates of the router, e.g. {@code /api/orders/{id}}, or the Ant-style patterns of Spring
 * Cloud Gateway:</p>
 * <ul>
 *     <li>{@code /prefix/**}: the prefix and every path under it, never {@code /prefixX};</li>
 *     <li>a {@code *} segment: any one segment, e.g. {@code /api/*}{@code /items};</li>
 *     <li>{@code {name}}: a segment captured as a path variable, e.g. for
 *     {@link GatewayFilters#setPath(String)}.</li>
 * </ul>
 * A {@code **} that does not end the pattern, a {@code ?}, or a wildcard inside a segment is
 * rejected. A route without a path pattern matches every path. The router matches the raw path
 * of a request as it is, and the gateway relays only paths without dot segments, encoded slashes
 * or path parameters, see {@link ProxyHandler}, so a path cannot route one way at the gateway
 * and another at the upstream.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface GatewayRouteBuilder {

    /**
     * Declare a route: the returned builder configures it, and the route is added once the
     * routes of the {@link GatewayRoutes} bean are declared.
     *
     * @param id The id of the route, unique among the gateway routes
     * @return The builder of the route
     */
    GatewayRoute.Builder route(String id);

    /**
     * Add a route.
     *
     * @param route The route
     * @return This builder
     */
    GatewayRouteBuilder route(GatewayRoute route);
}
