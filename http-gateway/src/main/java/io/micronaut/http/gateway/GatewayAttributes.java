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
 * The names of the attributes of a gateway route and of the requests it relays, e.g. for the
 * metrics, the tracing or the access log of a request.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayAttributes {

    /**
     * The id of the gateway route: an attribute of the route, see
     * {@link io.micronaut.web.router.RouteInfo#getAttribute(String)}, and of each request it
     * relays, a {@link String}.
     */
    public static final String ROUTE_ID = "micronaut.gateway.route-id";

    /**
     * The upstream of a request a gateway route relays, a {@link java.net.URI}: the URI of the
     * route, e.g. {@code lb://orders}, as an attribute of the route; the URI of the service
     * instance or of the server the request was last sent to, as an attribute of the request.
     */
    public static final String UPSTREAM = "micronaut.gateway.upstream";

    /**
     * The number of attempts it took to relay a request, an {@link Integer} attribute of the
     * request, set once the upstream answered or the last attempt failed.
     */
    public static final String ATTEMPTS = "micronaut.gateway.attempts";

    private GatewayAttributes() {
    }
}
