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
package io.micronaut.http.server.cors;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Strategy used by {@link CorsFilter} to resolve the {@link CorsOriginConfiguration} applicable
 * to a request origin and its matching routes.
 *
 * <p>Implementations may consider route-level {@link CrossOrigin} metadata as well as server-wide
 * CORS configuration. Returning {@code null} indicates that no configuration permits the supplied
 * origin.</p>
 *
 * @since 5.2.0
 */
@FunctionalInterface
@DefaultImplementation(DefaultCorsOriginConfigurationRetriever.class)
public interface CorsOriginConfigurationRetriever {
    /**
     * Resolves the CORS configuration for a request using its {@code Origin} header and the route
     * match stored in {@link RouteAttributes}. If the request has no associated route match, the
     * route list supplied to the primary retrieval method is empty.
     *
     * @param request the request for which to resolve CORS configuration
     * @return the applicable CORS configuration, or {@code null} when the request has no permitted
     * origin
     * @since 5.2.0
     */
    default @Nullable CorsOriginConfiguration findCorsOriginConfiguration(HttpRequest<?> request) {
        return findCorsOriginConfiguration(request.getOrigin().orElse(null),
            RouteAttributes.getRouteMatch(request).map(List::of).orElse(Collections.emptyList()));
    }

    /**
     * Resolves the CORS configuration for an origin and all routes matching the requested URI.
     * This form supports preflight processing, where the actual route has not yet been selected.
     *
     * @param origin the request origin, or {@code null} if it is absent
     * @param routeMatches the routes matching the requested URI; may be empty
     * @return the applicable CORS configuration, or {@code null} when no configuration permits
     * the supplied origin
     * @since 5.2.0
     */
    @Nullable CorsOriginConfiguration findCorsOriginConfiguration(@Nullable String origin,
                                                                  List<? extends RouteMatch<?>> routeMatches);
}
