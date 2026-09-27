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
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.web.router.builder.RequestCondition;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A gateway route: which requests it matches, and the upstream it relays them to with which
 * filters. The Java DSL, {@link GatewayRouteBuilder}, and the configuration,
 * {@code micronaut.gateway.routes}, both build one; it becomes an ordinary functional route, so
 * the server filters, e.g. of security and CORS, the error routes and the precedence of more
 * specific routes apply to it like to any route.
 *
 * <p>The URI is one of:</p>
 * <ul>
 *     <li>{@code http://host:port[/base]} or {@code https://…}: a server, whose base path, if
 *     any, is prepended to the path of each request; {@code ws://} and {@code wss://} are the
 *     same as {@code http://} and {@code https://}: every route relays WebSocket upgrades;</li>
 *     <li>{@code lb://service-id}: a service, whose instances the load balancer of the service
 *     selects, one per attempt, with the client configuration
 *     {@code micronaut.http.services.<service-id>.*}.</li>
 * </ul>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayRoute {

    /**
     * The scheme of a URI of a service, {@code lb://service-id}.
     */
    public static final String LOAD_BALANCED_SCHEME = "lb";

    private static final String WEBSOCKET = "websocket";

    private final String id;
    private final URI uri;
    private final List<String> paths;
    private final List<RequestCondition> conditions;
    private final int order;
    private final List<GatewayFilter> filters;
    @Nullable
    private final Duration responseTimeout;
    @Nullable
    private final Duration activityTimeout;
    @Nullable
    private final HttpVersionSelection httpVersion;
    @Nullable
    private final GatewayRetry retry;
    @Nullable
    private final GatewayCircuitBreaker circuitBreaker;
    private final Set<String> upgradeProtocols;
    @Nullable
    private final GatewayWeight weight;
    private final Map<String, Object> metadata;

    private GatewayRoute(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.uri = validate(Objects.requireNonNull(builder.uri, "The route " + builder.id + " has no URI"));
        this.paths = List.copyOf(builder.paths);
        this.conditions = List.copyOf(builder.conditions);
        this.order = builder.order;
        this.filters = List.copyOf(builder.filters);
        this.responseTimeout = builder.responseTimeout;
        this.activityTimeout = builder.activityTimeout;
        this.httpVersion = builder.httpVersion;
        this.retry = builder.retry;
        this.circuitBreaker = builder.circuitBreaker;
        this.weight = builder.weight;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
        this.upgradeProtocols = Set.copyOf(builder.upgradeProtocols);
    }

    private static URI validate(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("The URI of a gateway route must be absolute, http://, https:// or lb://: " + uri);
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (lower.equals(LOAD_BALANCED_SCHEME)) {
            if (uri.getHost() == null && uri.getAuthority() == null) {
                throw new IllegalArgumentException("The URI of a service is lb://service-id: " + uri);
            }
        } else if (!lower.equals("http") && !lower.equals("https") && !lower.equals("ws") && !lower.equals("wss")) {
            throw new IllegalArgumentException("The URI of a gateway route must be http://, https://, ws://, wss:// or lb://: " + uri);
        } else if (uri.getHost() == null) {
            throw new IllegalArgumentException("The URI of a gateway route has no host: " + uri);
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("The URI of a gateway route has no query or fragment: " + uri);
        }
        return uri;
    }

    /**
     * @param id The id of the route
     * @return A builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * @return A builder with the settings of this route
     */
    public Builder toBuilder() {
        Builder builder = new Builder(id).uri(uri).order(order)
            .responseTimeout(responseTimeout)
            .activityTimeout(activityTimeout)
            .httpVersion(httpVersion)
            .retry(retry)
            .circuitBreaker(circuitBreaker);
        builder.upgradeProtocols.clear();
        builder.upgradeProtocols.addAll(upgradeProtocols);
        builder.weight = weight;
        builder.paths.addAll(paths);
        builder.conditions.addAll(conditions);
        builder.filters.addAll(filters);
        builder.metadata.putAll(metadata);
        return builder;
    }

    /**
     * @return The id of the route
     */
    public String getId() {
        return id;
    }

    /**
     * @return The URI of the upstream
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @return Whether the upstream is a service, {@code lb://service-id}
     */
    public boolean isLoadBalanced() {
        return LOAD_BALANCED_SCHEME.equalsIgnoreCase(uri.getScheme());
    }

    /**
     * @return The service id of a {@link #isLoadBalanced() load balanced} route
     */
    public String getServiceId() {
        String host = uri.getHost();
        return host != null ? host : Objects.requireNonNull(uri.getAuthority());
    }

    /**
     * @return The path patterns of the route, see {@link Builder#path(String...)}
     */
    public List<String> getPaths() {
        return paths;
    }

    /**
     * @return The templates of the functional routes the route is registered as, e.g.
     * {@code /api/orders} and {@code /api/orders/{+path}} for {@code /api/orders/**}
     */
    public List<String> getRouteTemplates() {
        List<String> templates = new ArrayList<>();
        for (String path : paths.isEmpty() ? List.of("/**") : paths) {
            templates.addAll(GatewayRouteSpec.templates(path));
        }
        return List.copyOf(templates);
    }

    /**
     * @return The conditions a request meets, all of them
     */
    public List<RequestCondition> getConditions() {
        return conditions;
    }

    /**
     * @return The order of the route, see {@link io.micronaut.web.router.builder.RouteSpec#order(int)}
     */
    public int getOrder() {
        return order;
    }

    /**
     * @return The filters, in the order they apply to the upstream request
     */
    public List<GatewayFilter> getFilters() {
        return filters;
    }

    /**
     * @return The time to wait for the headers of the upstream response once the request is sent,
     * or {@code null} for the read timeout of the client
     */
    public @Nullable Duration getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * @return The idle timeout of a connection that switched protocols, e.g. a WebSocket, or
     * {@code null} for none
     */
    public @Nullable Duration getActivityTimeout() {
        return activityTimeout;
    }

    /**
     * @return The HTTP version of the upstream connection, or {@code null} for the one of the
     * client configuration
     */
    public @Nullable HttpVersionSelection getHttpVersion() {
        return httpVersion;
    }

    /**
     * @return The retry policy, or {@code null} for none
     */
    public @Nullable GatewayRetry getRetry() {
        return retry;
    }

    /**
     * @return The weight of the route in a group of routes that split the traffic, if any
     */
    public @Nullable GatewayWeight getWeight() {
        return weight;
    }

    /**
     * @return The circuit breaker of the route, if any
     */
    public @Nullable GatewayCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * @return Free-form metadata of the route, e.g. for the gateway endpoint
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * The protocols a request of the route may switch the connection to, {@code websocket} by
     * default, in lower case. Once the upstream switched protocols, the bytes of the connection
     * are relayed as they are, past the filters and the limits of the route: a request that asks
     * for another protocol is relayed without its {@code Upgrade} header.
     *
     * @return The protocols
     */
    public Set<String> getUpgradeProtocols() {
        return upgradeProtocols;
    }

    @Override
    public String toString() {
        return "GatewayRoute{id=" + id + ", uri=" + RelayHeaders.redact(uri) + ", paths=" + paths + ", order=" + order + '}';
    }

    /**
     * A builder of a {@link GatewayRoute}.
     */
    public static final class Builder {
        private final String id;
        @Nullable
        private URI uri;
        private final List<String> paths = new ArrayList<>(1);
        private final List<RequestCondition> conditions = new ArrayList<>(1);
        private int order;
        private final List<GatewayFilter> filters = new ArrayList<>();
        @Nullable
        private Duration responseTimeout;
        @Nullable
        private Duration activityTimeout;
        @Nullable
        private HttpVersionSelection httpVersion;
        @Nullable
        private GatewayRetry retry;
        @Nullable
        private GatewayCircuitBreaker circuitBreaker;
        @Nullable
        private GatewayWeight weight;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final Set<String> upgradeProtocols = new LinkedHashSet<>(List.of(WEBSOCKET));

        private Builder(String id) {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("The id of a gateway route is blank");
            }
            this.id = id;
        }

        /**
         * @param uri The URI of the upstream
         * @return This builder
         */
        public Builder uri(URI uri) {
            this.uri = Objects.requireNonNull(uri, "uri");
            return this;
        }

        /**
         * @param uri The URI of the upstream
         * @return This builder
         */
        public Builder uri(String uri) {
            return uri(URI.create(Objects.requireNonNull(uri, "uri").strip()));
        }

        /**
         * @param paths Path patterns to add
         * @return This builder
         */
        public Builder path(String... paths) {
            for (String path : paths) {
                this.paths.add(Objects.requireNonNull(path, "path"));
            }
            return this;
        }

        /**
         * @param conditions Conditions to add
         * @return This builder
         */
        public Builder where(RequestCondition... conditions) {
            for (RequestCondition condition : conditions) {
                this.conditions.add(Objects.requireNonNull(condition, "condition"));
            }
            return this;
        }

        /**
         * @param order The order
         * @return This builder
         */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /**
         * @param filters Filters to add, after those the route has
         * @return This builder
         */
        public Builder filters(GatewayFilter... filters) {
            for (GatewayFilter filter : filters) {
                filter(filter);
            }
            return this;
        }

        /**
         * @param filters Filters to add, after those the route has
         * @return This builder
         */
        public Builder filters(List<? extends GatewayFilter> filters) {
            for (GatewayFilter filter : filters) {
                filter(filter);
            }
            return this;
        }

        /**
         * @param filters The filters that replace those of the route
         * @return This builder
         */
        Builder replaceFilters(List<? extends GatewayFilter> filters) {
            this.filters.clear();
            return filters(filters);
        }

        private void filter(GatewayFilter filter) {
            Objects.requireNonNull(filter, "filter");
            if (filter instanceof GatewayRetry r) {
                retry(r);
            } else if (filter instanceof GatewayCircuitBreaker cb) {
                circuitBreaker(cb);
            } else {
                filters.add(filter);
            }
        }

        /**
         * @param responseTimeout See {@link GatewayRoute#getResponseTimeout()}
         * @return This builder
         */
        public Builder responseTimeout(@Nullable Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
            return this;
        }

        /**
         * @param activityTimeout See {@link GatewayRoute#getActivityTimeout()}
         * @return This builder
         */
        public Builder activityTimeout(@Nullable Duration activityTimeout) {
            this.activityTimeout = activityTimeout;
            return this;
        }

        /**
         * @param httpVersion See {@link GatewayRoute#getHttpVersion()}
         * @return This builder
         */
        public Builder httpVersion(@Nullable HttpVersionSelection httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        /**
         * @param retry See {@link GatewayRoute#getRetry()}
         * @return This builder
         */
        public Builder retry(@Nullable GatewayRetry retry) {
            this.retry = retry;
            return this;
        }

        /**
         * @param circuitBreaker See {@link GatewayRoute#getCircuitBreaker()}
         * @return This builder
         */
        public Builder circuitBreaker(@Nullable GatewayCircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        /**
         * @param protocols See {@link GatewayRoute#getUpgradeProtocols()}, none to relay no upgrade
         * @return This builder
         */
        public Builder upgradeProtocols(String... protocols) {
            upgradeProtocols.clear();
            for (String protocol : protocols) {
                upgradeProtocols.add(Objects.requireNonNull(protocol, "protocol").strip().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        /**
         * Put the route into a group of routes that split the traffic by weight, see
         * {@link GatewayWeight}.
         *
         * @param group  The name of the group
         * @param weight The weight of the route in the group
         * @return This builder
         */
        public Builder weight(String group, int weight) {
            this.weight = GatewayWeight.of(group, weight);
            return this;
        }

        /**
         * @param name  The name of the entry
         * @param value The value
         * @return This builder
         */
        public Builder metadata(String name, Object value) {
            metadata.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * @return The route
         */
        public GatewayRoute build() {
            return new GatewayRoute(this);
        }
    }
}
