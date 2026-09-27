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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.PathVariables;

import java.net.URI;

/**
 * A request a gateway route relays, as the {@link GatewayFilter proxy transforms} see it: the
 * inbound request, with the path variables of its route, and the settings of the exchange with
 * the upstream the transforms may change. A transform changes the upstream request itself, which
 * it is given separately.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ProxyExchange {

    private final HttpRequest<?> request;
    private final PathVariables pathVariables;
    private final String routeId;
    private final URI uri;
    private final boolean xForwarded;
    private boolean retainHostHeader;
    private volatile URI target;

    ProxyExchange(HttpRequest<?> request, PathVariables pathVariables, String routeId, URI uri, boolean xForwarded) {
        this.request = request;
        this.pathVariables = pathVariables;
        this.routeId = routeId;
        this.uri = uri;
        this.target = uri;
        this.xForwarded = xForwarded;
    }

    /**
     * @return The inbound request, as the route received it
     */
    public HttpRequest<?> request() {
        return request;
    }

    /**
     * @return The path variables of the route of the request
     */
    public PathVariables pathVariables() {
        return pathVariables;
    }

    /**
     * @return The id of the gateway route
     */
    public String routeId() {
        return routeId;
    }

    /**
     * @return The URI of the route, e.g. {@code lb://orders} or {@code http://orders:8080}
     */
    public URI uri() {
        return uri;
    }

    /**
     * @return The URI of the server the request was last sent to: the URI of the route, or of
     * the service instance the load balancer selected for the attempt
     */
    public URI target() {
        return target;
    }

    void target(URI target) {
        this.target = target;
    }

    /**
     * @return Whether the gateway sends the {@code X-Forwarded-*} headers, e.g. for
     * {@link GatewayFilters#stripPrefix(int)} to send the prefix it removes as
     * {@code X-Forwarded-Prefix}
     */
    public boolean isXForwarded() {
        return xForwarded;
    }

    /**
     * @return Whether the upstream request keeps the {@code Host} header it has, rather than one
     * computed from the URI of the upstream
     */
    public boolean isRetainHostHeader() {
        return retainHostHeader;
    }

    /**
     * Keep the {@code Host} header of the upstream request, e.g. the host of the inbound request
     * for {@link GatewayFilters#preserveHostHeader()}, rather than one computed from the URI of
     * the upstream.
     *
     * @param retainHostHeader Whether to keep it
     */
    public void setRetainHostHeader(boolean retainHostHeader) {
        this.retainHostHeader = retainHostHeader;
    }
}
