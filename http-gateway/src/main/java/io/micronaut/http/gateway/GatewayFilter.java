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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.builder.RouteFilterSpec;

/**
 * A filter of a gateway route. It is one or both of:
 * <ul>
 *     <li>a <em>proxy transform</em>, applied by the {@link ProxyHandler} to the upstream request
 *     once, before the first attempt, and to the upstream response once it arrives, while its
 *     body still streams. It does not change the inbound request, which the server filters,
 *     e.g. of security, logging or metrics, see as the client sent it;</li>
 *     <li>a <em>route filter</em>, declared on the functional route of the gateway route, see
 *     {@link #configure(RouteFilterSpec)}, e.g. to answer before anything is relayed, or to change
 *     the final response.</li>
 * </ul>
 * The built-in filters are in {@link GatewayFilters}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface GatewayFilter {

    /**
     * Change the upstream request before it is sent: its URI, a path and a raw query relative to
     * the upstream, and its headers.
     *
     * @param exchange The exchange
     * @param request  The upstream request
     * @throws Exception If the request cannot be relayed, answered like an exception of a route
     */
    default void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) throws Exception {
    }

    /**
     * Change the upstream response before it is relayed: its status and headers. Its body is
     * relayed as it streams.
     *
     * @param exchange The exchange
     * @param response The upstream response
     * @throws Exception If the response cannot be relayed
     */
    default void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) throws Exception {
    }

    /**
     * Declare what the filter needs on the functional route of the gateway route, e.g. a route
     * filter.
     *
     * @param route The route
     */
    default void configure(RouteFilterSpec<?> route) {
    }
}
