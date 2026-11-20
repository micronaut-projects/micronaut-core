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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.server.util.ForwardedHeaders;
import io.micronaut.web.router.builder.Cidr;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The configuration of the gateway, {@code micronaut.gateway.*}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@ConfigurationProperties(GatewayConfiguration.PREFIX)
public class GatewayConfiguration {

    /**
     * The prefix of the configuration of the gateway.
     */
    public static final String PREFIX = "micronaut.gateway";

    private boolean enabled = true;
    private List<String> defaultFilters = new ArrayList<>();
    private ForwardedConfiguration forwarded = new ForwardedConfiguration();
    @Nullable
    private Duration refreshInterval;

    /**
     * @return Whether the gateway routes are registered
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Whether the gateway routes are registered, default true
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return How often the routes of the {@link GatewayRouteLocator route locators} are
     * refreshed, e.g. to follow the services of a discovery client, or {@code null}, the default,
     * to refresh them only on demand
     */
    public @Nullable Duration getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @param refreshInterval See {@link #getRefreshInterval()}
     */
    public void setRefreshInterval(@Nullable Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    /**
     * @return The filters of every gateway route, applied before the filters of the route, in
     * the shortcut syntax, e.g. {@code DedupeResponseHeader=Access-Control-Allow-Origin}
     */
    public List<String> getDefaultFilters() {
        return defaultFilters;
    }

    /**
     * @param defaultFilters The filters of every gateway route, in the shortcut syntax
     */
    public void setDefaultFilters(List<String> defaultFilters) {
        this.defaultFilters = defaultFilters;
    }

    /**
     * @return The forwarded headers the gateway sends upstream
     */
    public ForwardedConfiguration getForwarded() {
        return forwarded;
    }

    /**
     * @param forwarded The forwarded headers the gateway sends upstream
     */
    public void setForwarded(ForwardedConfiguration forwarded) {
        this.forwarded = forwarded;
    }

    /**
     * The forwarded headers the gateway sends upstream, {@code micronaut.gateway.forwarded.*}:
     * the gateway replaces the forwarded headers of a client that is not a trusted proxy, and
     * appends itself to those of a trusted proxy.
     */
    @ConfigurationProperties("forwarded")
    public static class ForwardedConfiguration {
        private boolean xForwardedHeaders = true;
        private boolean forwardedHeader = true;
        private List<String> trustedProxies = new ArrayList<>();

        /**
         * @return Whether to send the {@code X-Forwarded-*} headers
         */
        public boolean isXForwardedHeaders() {
            return xForwardedHeaders;
        }

        /**
         * @param xForwardedHeaders Whether to send the {@code X-Forwarded-*} headers, default true
         */
        public void setXForwardedHeaders(boolean xForwardedHeaders) {
            this.xForwardedHeaders = xForwardedHeaders;
        }

        /**
         * @return Whether to send the {@code Forwarded} header of RFC 7239
         */
        public boolean isForwardedHeader() {
            return forwardedHeader;
        }

        /**
         * @param forwardedHeader Whether to send the {@code Forwarded} header of RFC 7239, default true
         */
        public void setForwardedHeader(boolean forwardedHeader) {
            this.forwardedHeader = forwardedHeader;
        }

        /**
         * @return The address ranges of the trusted proxies, in CIDR notation
         */
        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        /**
         * @param trustedProxies The address ranges of the proxies whose forwarded headers are
         *                       kept and appended to, in CIDR notation, e.g. {@code 10.0.0.0/8};
         *                       default none: the forwarded headers of a client are replaced
         */
        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }

        /**
         * @return The forwarded headers of the configuration
         */
        public ForwardedHeaders toForwardedHeaders() {
            List<Cidr> ranges = new ArrayList<>(trustedProxies.size());
            for (String range : trustedProxies) {
                ranges.add(Cidr.parse(range));
            }
            return ForwardedHeaders.builder()
                .xForwarded(xForwardedHeaders)
                .forwarded(forwardedHeader)
                .trustedProxy((InetSocketAddress address) -> trusted(ranges, address))
                .build();
        }

        private static boolean trusted(List<Cidr> ranges, InetSocketAddress address) {
            if (ranges.isEmpty() || address.getAddress() == null) {
                return false;
            }
            byte[] bytes = Cidr.address(address.getAddress().getHostAddress());
            if (bytes == null) {
                return false;
            }
            for (Cidr range : ranges) {
                if (range.contains(bytes)) {
                    return true;
                }
            }
            return false;
        }
    }
}
