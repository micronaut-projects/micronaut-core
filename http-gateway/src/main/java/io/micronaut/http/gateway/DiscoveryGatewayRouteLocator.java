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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * A route per service of the discovery client, like Spring Cloud Gateway's discovery locator:
 * the requests under {@code /<service-id>/} go to {@code lb://<service-id>}, without the prefix.
 * Enabled with {@code micronaut.gateway.discovery-locator.enabled}; a service that appears or
 * goes away gets or loses its route on the next refresh, e.g. every
 * {@code micronaut.gateway.refresh-interval}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Singleton
@Requires(property = DiscoveryGatewayRouteLocator.Configuration.PREFIX + ".enabled", value = StringUtils.TRUE)
@Requires(beans = DiscoveryClient.class)
public final class DiscoveryGatewayRouteLocator implements GatewayRouteLocator {
    private final DiscoveryClient discoveryClient;
    private final Configuration configuration;

    /**
     * @param discoveryClient The discovery client
     * @param configuration   The configuration
     */
    public DiscoveryGatewayRouteLocator(DiscoveryClient discoveryClient, Configuration configuration) {
        this.discoveryClient = discoveryClient;
        this.configuration = configuration;
    }

    @Override
    public CompletionStage<List<GatewayRoute>> getRoutes() {
        Pattern include = configuration.getIncludePattern() == null ? null : Pattern.compile(configuration.getIncludePattern());
        return DefaultProxyHandler.first(discoveryClient.getServiceIds()).thenApply(found -> {
            List<String> ids = found == null ? List.of() : found;
            List<GatewayRoute> routes = new ArrayList<>(ids.size());
            for (String id : ids) {
                if (include != null && !include.matcher(id).matches()) {
                    continue;
                }
                String prefix = configuration.isLowerCaseServiceId() ? id.toLowerCase(Locale.ROOT) : id;
                routes.add(GatewayRoute.builder(configuration.getRouteIdPrefix() + id)
                    .path("/" + prefix + "/**")
                    .filters(GatewayFilters.stripPrefix(1))
                    .uri(configuration.getUriTemplate().replace("{serviceId}", id))
                    .build());
            }
            return routes;
        });
    }

    /**
     * The configuration of the discovery locator, {@code micronaut.gateway.discovery-locator}.
     */
    @ConfigurationProperties(Configuration.PREFIX)
    public static class Configuration {
        /**
         * The prefix of the configuration.
         */
        public static final String PREFIX = GatewayConfiguration.PREFIX + ".discovery-locator";

        private boolean enabled;
        @Nullable
        private String includePattern;
        private boolean lowerCaseServiceId;
        private String routeIdPrefix = "discovery_";
        private String uriTemplate = "lb://{serviceId}";

        /**
         * @return Whether the locator is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled Whether the locator is enabled, default false
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return A regular expression the ids of the services with a route match, or
         * {@code null} for every service
         */
        public @Nullable String getIncludePattern() {
            return includePattern;
        }

        /**
         * @param includePattern See {@link #getIncludePattern()}
         */
        public void setIncludePattern(@Nullable String includePattern) {
            this.includePattern = includePattern;
        }

        /**
         * @return Whether the path prefix is the service id in lower case
         */
        public boolean isLowerCaseServiceId() {
            return lowerCaseServiceId;
        }

        /**
         * @param lowerCaseServiceId See {@link #isLowerCaseServiceId()}, default false
         */
        public void setLowerCaseServiceId(boolean lowerCaseServiceId) {
            this.lowerCaseServiceId = lowerCaseServiceId;
        }

        /**
         * @return The prefix of the ids of the routes, default {@code discovery_}
         */
        public String getRouteIdPrefix() {
            return routeIdPrefix;
        }

        /**
         * @param routeIdPrefix See {@link #getRouteIdPrefix()}
         */
        public void setRouteIdPrefix(String routeIdPrefix) {
            this.routeIdPrefix = routeIdPrefix;
        }

        /**
         * @return The URI of the route of a service, {@code {serviceId}} replaced by its id;
         * default {@code lb://{serviceId}}
         */
        public String getUriTemplate() {
            return uriTemplate;
        }

        /**
         * @param uriTemplate See {@link #getUriTemplate()}
         */
        public void setUriTemplate(String uriTemplate) {
            this.uriTemplate = uriTemplate;
        }
    }
}
