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
import java.util.IdentityHashMap;
import java.util.Map;
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

    // null for the state of the route filters of a request no gateway route relays
    private @Nullable GatewayRoute route;
    // written by the relay before the futures the readers wait on complete
    private @Nullable URI upstream;
    private int attempts;
    private @Nullable Throwable failure;
    private boolean bodyKeptForReplay;
    private byte @Nullable [] cachedRequestBody;
    // the state of the route filters of the request, keyed by the filter: one map, not an attribute each
    private @Nullable Map<Object, Object> state;

    private GatewayRequestContext(@Nullable GatewayRoute route) {
        this.route = route;
    }

    /**
     * @param request A request
     * @return The context of the gateway route that relays it, empty if no gateway route matched it
     */
    public static Optional<GatewayRequestContext> find(HttpRequest<?> request) {
        GatewayRequestContext context = existing(request);
        return context == null || context.route == null ? Optional.empty() : Optional.of(context);
    }

    private static @Nullable GatewayRequestContext existing(HttpRequest<?> request) {
        return request.getAttribute(KEY).orElse(null) instanceof GatewayRequestContext context ? context : null;
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
        Objects.requireNonNull(route, "route");
        GatewayRequestContext context = existing(request);
        if (context != null && (context.route == route || context.route == null)) {
            // the route filters ran before the handler
            context.route = route;
            return context;
        }
        context = new GatewayRequestContext(route);
        request.setAttribute(KEY, context);
        return context;
    }

    /**
     * The context the route filters of a request keep their state in, also on a route that is not
     * a gateway route.
     *
     * @param request The request
     * @return The context
     */
    static GatewayRequestContext forFilters(HttpRequest<?> request) {
        GatewayRequestContext context = existing(request);
        if (context == null) {
            context = new GatewayRequestContext(null);
            request.setAttribute(KEY, context);
        }
        return context;
    }

    /**
     * @param request A request
     * @return The id of the gateway route of the request, {@code -} if it has none
     */
    static String routeId(HttpRequest<?> request) {
        GatewayRequestContext context = existing(request);
        return context == null ? "-" : context.routeId();
    }

    /**
     * @param owner The filter that keeps the state
     * @param type  The type of the state
     * @param <T>   The type of the state
     * @return The state the filter kept for the request, or {@code null}
     */
    synchronized <T> @Nullable T state(Object owner, Class<T> type) {
        return state == null ? null : type.cast(state.get(owner));
    }

    /**
     * @param owner The filter that kept the state
     * @return The state it removed, or {@code null}
     */
    synchronized @Nullable Object removeState(Object owner) {
        return state == null ? null : state.remove(owner);
    }

    /**
     * @param owner The filter that keeps the state
     * @param value The state
     * @return The state it replaced, or {@code null}
     */
    synchronized @Nullable Object state(Object owner, Object value) {
        if (state == null) {
            state = new IdentityHashMap<>(4);
        }
        return state.put(owner, value);
    }

    /**
     * @return The gateway route
     */
    public GatewayRoute route() {
        return Objects.requireNonNull(route, "route");
    }

    /**
     * @return The id of the gateway route
     */
    public String routeId() {
        GatewayRoute r = route;
        return r == null ? "-" : r.getId();
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

    /**
     * @return Whether the body of the request, which streams in, is kept to send it again, see
     * {@link GatewayRetry#maxReplayBytes()}: only for a method the retries of the route send again
     */
    public boolean bodyKeptForReplay() {
        return bodyKeptForReplay;
    }

    /**
     * @return The bytes of the body of the request, for the filters of a route with
     * {@link GatewayFilters#cacheRequestBody(long)}; empty for a request without a body, and
     * without that filter
     */
    public Optional<byte[]> cachedRequestBody() {
        return Optional.ofNullable(cachedRequestBody);
    }

    void cachedRequestBody(byte[] bytes) {
        this.cachedRequestBody = bytes;
    }

    void keepBodyForReplay() {
        bodyKeptForReplay = true;
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
