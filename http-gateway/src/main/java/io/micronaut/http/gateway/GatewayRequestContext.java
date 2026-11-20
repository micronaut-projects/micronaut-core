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
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * What a gateway route knows about a request it relays: its route, the upstream the request was
 * last sent to, the number of attempts, and the failure a fallback answers for. Read it with
 * {@link #find(HttpRequest)}, e.g. in a fallback handler, an error route, a filter or an access
 * log, once the route matched the request.
 *
 * <p>The state is held by one object per request, set once, so a route does not pay for an
 * attribute per value.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayRequestContext {

    private static final String KEY = GatewayRequestContext.class.getName();

    private final GatewayRoute route;
    // written by the relay before the futures the readers wait on complete
    private @Nullable URI upstream;
    private int attempts;
    private @Nullable Throwable failure;

    private GatewayRequestContext(GatewayRoute route) {
        this.route = route;
    }

    /**
     * @param request A request
     * @return The context of the gateway route that relays it, empty if no gateway route matched it
     */
    public static Optional<GatewayRequestContext> find(HttpRequest<?> request) {
        return request.getAttribute(KEY, GatewayRequestContext.class);
    }

    /**
     * The context of a request of a route, created by the first filter or handler of the route
     * that needs it.
     *
     * @param request The request
     * @param route   The gateway route
     * @return The context
     */
    static GatewayRequestContext of(HttpRequest<?> request, GatewayRoute route) {
        Object existing = request.getAttribute(KEY).orElse(null);
        if (existing instanceof GatewayRequestContext context && context.route == route) {
            return context;
        }
        GatewayRequestContext context = new GatewayRequestContext(Objects.requireNonNull(route, "route"));
        request.setAttribute(KEY, context);
        return context;
    }

    /**
     * @return The gateway route
     */
    public GatewayRoute route() {
        return route;
    }

    /**
     * @return The id of the gateway route
     */
    public String routeId() {
        return route.getId();
    }

    /**
     * @return The URI of the service instance or of the server the request was last sent to, empty
     * before the first attempt
     */
    public Optional<URI> upstream() {
        return Optional.ofNullable(upstream);
    }

    /**
     * @return The number of attempts made so far
     */
    public int attempts() {
        return attempts;
    }

    /**
     * @return The failure a {@link GatewayCircuitBreaker#fallback() fallback} answers for: an
     * {@link io.micronaut.retry.exception.CircuitOpenException} while the circuit is open, or the
     * failure of the last attempt; empty if the relay did not fail
     */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    void upstream(URI upstream) {
        this.upstream = upstream;
    }

    void attempts(int attempts) {
        this.attempts = attempts;
    }

    void failure(Throwable failure) {
        this.failure = failure;
    }
}
