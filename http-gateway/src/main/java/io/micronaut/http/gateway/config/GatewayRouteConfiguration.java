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
package io.micronaut.http.gateway.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.gateway.GatewayConfiguration;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A gateway route of the configuration, {@code micronaut.gateway.routes.<id>}, e.g.
 * <pre>
 * micronaut:
 *   gateway:
 *     routes:
 *       orders:
 *         uri: lb://orders
 *         predicates:
 *           - Path=/api/orders/**
 *           - Header=X-Tenant, \w+
 *         filters:
 *           - StripPrefix=2
 *           - name: Retry
 *             args:
 *               retries: 2
 * </pre>
 * The predicates and filters are given in the shortcut syntax, {@code Name=arg1, arg2}, or by
 * {@code name} and {@code args}, and are created by the {@link GatewayPredicateFactory} and
 * {@link GatewayFilterFactory} beans of their names.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@EachProperty(GatewayRouteConfiguration.PREFIX)
public class GatewayRouteConfiguration {

    /**
     * The prefix of the routes of the configuration.
     */
    public static final String PREFIX = GatewayConfiguration.PREFIX + ".routes";

    private final String id;
    @Nullable
    private String uri;
    private int order;
    private boolean enabled = true;
    private List<Object> predicates = new ArrayList<>();
    private List<Object> filters = new ArrayList<>();
    @Nullable
    private Duration responseTimeout;
    @Nullable
    private Duration activityTimeout;
    @Nullable
    private HttpVersion httpVersion;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Nullable
    private List<String> upgradeProtocols;

    /**
     * @param id The id of the route
     */
    public GatewayRouteConfiguration(@Parameter String id) {
        this.id = id;
    }

    /**
     * @return The id of the route, the key of its configuration
     */
    public String getId() {
        return id;
    }

    /**
     * @return The URI of the upstream, {@code http://}, {@code https://} or {@code lb://}
     */
    public @Nullable String getUri() {
        return uri;
    }

    /**
     * @param uri The URI of the upstream
     */
    public void setUri(@Nullable String uri) {
        this.uri = uri;
    }

    /**
     * @return The order of the route
     */
    public int getOrder() {
        return order;
    }

    /**
     * @param order The order of the route, lower wins a tie between equally specific routes, default 0
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * @return Whether the route is registered
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Whether the route is registered, default true
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The predicates: shortcut strings, or maps with a {@code name} and {@code args}
     */
    public List<Object> getPredicates() {
        return predicates;
    }

    /**
     * @param predicates The predicates
     */
    public void setPredicates(List<Object> predicates) {
        this.predicates = predicates;
    }

    /**
     * @return The filters: shortcut strings, or maps with a {@code name} and {@code args}
     */
    public List<Object> getFilters() {
        return filters;
    }

    /**
     * @param filters The filters
     */
    public void setFilters(List<Object> filters) {
        this.filters = filters;
    }

    /**
     * @return The time to wait for the headers of the upstream response once the request is sent
     */
    public @Nullable Duration getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * @param responseTimeout The time to wait for the headers of the upstream response; it can
     *                        only shorten the read timeout of the client
     */
    public void setResponseTimeout(@Nullable Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    /**
     * @return The idle timeout of a switched connection, e.g. a WebSocket
     */
    public @Nullable Duration getActivityTimeout() {
        return activityTimeout;
    }

    /**
     * @param activityTimeout The idle timeout of a switched connection, e.g. a WebSocket
     */
    public void setActivityTimeout(@Nullable Duration activityTimeout) {
        this.activityTimeout = activityTimeout;
    }

    /**
     * @return The protocols a request may switch the connection to, {@code websocket} if not set
     */
    public @Nullable List<String> getUpgradeProtocols() {
        return upgradeProtocols;
    }

    /**
     * @param upgradeProtocols The protocols a request may switch the connection to, see
     *                         {@link io.micronaut.http.gateway.GatewayRoute#getUpgradeProtocols()}
     */
    public void setUpgradeProtocols(@Nullable List<String> upgradeProtocols) {
        this.upgradeProtocols = upgradeProtocols;
    }

    /**
     * @return The HTTP version of the upstream connection
     */
    public @Nullable HttpVersion getHttpVersion() {
        return httpVersion;
    }

    /**
     * @param httpVersion The HTTP version of the upstream connection, e.g. {@code HTTP_2_0};
     *                    default the version of the client configuration
     */
    public void setHttpVersion(@Nullable HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    /**
     * @return Free-form metadata of the route
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * @param metadata Free-form metadata of the route
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
