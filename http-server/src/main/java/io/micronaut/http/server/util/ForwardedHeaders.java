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
package io.micronaut.http.server.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import org.jspecify.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Writes the forwarding headers of a request relayed to an upstream server:
 * {@code X-Forwarded-For}, {@code X-Forwarded-Proto}, {@code X-Forwarded-Host},
 * {@code X-Forwarded-Port}, {@code X-Forwarded-Prefix} and the RFC 7239 {@code Forwarded}
 * header.
 * <p>Whether the forwarding headers that the inbound request already carries are kept depends on
 * whether the peer that sent it is a {@link Builder#trustedProxy(Predicate) trusted proxy}:
 * <ul>
 *     <li>From a trusted proxy, this hop is appended to {@code X-Forwarded-For} and
 *     {@code Forwarded}, and the {@code X-Forwarded-Proto}, {@code -Host}, {@code -Port} and
 *     {@code -Prefix} values the first proxy set are kept.</li>
 *     <li>From any other peer, the inbound values could be forged, so they are replaced with the
 *     values of this hop.</li>
 * </ul>
 * By default no peer is trusted.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ForwardedHeaders {
    /**
     * The {@code X-Forwarded-For} header.
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    /**
     * The {@code X-Forwarded-Proto} header.
     */
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    /**
     * The {@code X-Forwarded-Host} header.
     */
    public static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    /**
     * The {@code X-Forwarded-Port} header.
     */
    public static final String X_FORWARDED_PORT = "X-Forwarded-Port";
    /**
     * The {@code X-Forwarded-Prefix} header.
     */
    public static final String X_FORWARDED_PREFIX = "X-Forwarded-Prefix";

    private static final ForwardedHeaders DEFAULT = builder().build();
    private static final List<String> X_FORWARDED_HEADERS = List.of(X_FORWARDED_FOR, X_FORWARDED_PROTO, X_FORWARDED_HOST, X_FORWARDED_PORT, X_FORWARDED_PREFIX);
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    private final Predicate<? super InetSocketAddress> trustedProxy;
    private final boolean xForwarded;
    private final boolean forwarded;
    @Nullable
    private final String prefix;

    private ForwardedHeaders(Builder builder) {
        this.trustedProxy = builder.trustedProxy;
        this.xForwarded = builder.xForwarded;
        this.forwarded = builder.forwarded;
        this.prefix = builder.prefix;
    }

    /**
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Write the forwarding headers with the default settings: all headers are written, and no
     * peer is trusted.
     *
     * @param inbound  The request received by this server
     * @param outbound The request that relays it upstream
     */
    public static void apply(HttpRequest<?> inbound, MutableHttpRequest<?> outbound) {
        DEFAULT.write(inbound, outbound);
    }

    /**
     * Write the forwarding headers onto the outbound request, replacing any it already has
     * (e.g. copied from the inbound request).
     *
     * @param inbound  The request received by this server
     * @param outbound The request that relays it upstream
     */
    public void write(HttpRequest<?> inbound, MutableHttpRequest<?> outbound) {
        HttpHeaders in = inbound.getHeaders();
        MutableHttpHeaders out = outbound.getHeaders();
        InetSocketAddress remoteAddress = inbound.getRemoteAddress();
        boolean trusted = remoteAddress != null && trustedProxy.test(remoteAddress);

        String clientAddress = remoteAddress == null ? null : remoteAddress.getHostString();
        String proto = inbound.isSecure() ? "https" : "http";
        String hostHeader = in.get(HttpHeaders.HOST);
        String host = hostHeader;
        int port = inbound.isSecure() ? HTTPS_PORT : HTTP_PORT;
        if (hostHeader != null) {
            int portSeparator = hostHeader.lastIndexOf(':');
            if (portSeparator > hostHeader.lastIndexOf(']')) {
                host = hostHeader.substring(0, portSeparator);
                try {
                    port = Integer.parseInt(hostHeader.substring(portSeparator + 1));
                } catch (NumberFormatException e) {
                    host = hostHeader;
                }
            }
        }

        for (String name : X_FORWARDED_HEADERS) {
            out.remove(name);
        }
        out.remove(HttpHeaders.FORWARDED);

        if (xForwarded) {
            String forwardedFor = clientAddress == null ? "unknown" : clientAddress;
            String inboundFor = trusted ? join(in.getAll(X_FORWARDED_FOR)) : null;
            out.set(X_FORWARDED_FOR, inboundFor == null ? forwardedFor : inboundFor + ", " + forwardedFor);
            String inboundProto = trusted ? in.get(X_FORWARDED_PROTO) : null;
            out.set(X_FORWARDED_PROTO, inboundProto == null ? proto : inboundProto);
            String inboundHost = trusted ? in.get(X_FORWARDED_HOST) : null;
            if (inboundHost != null) {
                out.set(X_FORWARDED_HOST, inboundHost);
            } else if (host != null) {
                out.set(X_FORWARDED_HOST, host);
            }
            String inboundPort = trusted ? in.get(X_FORWARDED_PORT) : null;
            out.set(X_FORWARDED_PORT, inboundPort == null ? String.valueOf(port) : inboundPort);
            String inboundPrefix = trusted ? in.get(X_FORWARDED_PREFIX) : null;
            String forwardedPrefix = inboundPrefix == null ? prefix : prefix == null ? inboundPrefix : inboundPrefix + prefix;
            if (forwardedPrefix != null) {
                out.set(X_FORWARDED_PREFIX, forwardedPrefix);
            }
        }
        if (forwarded) {
            StringBuilder element = new StringBuilder("for=").append(node(clientAddress)).append(";proto=").append(proto);
            if (hostHeader != null) {
                element.append(";host=").append(quoteIfNeeded(hostHeader));
            }
            String inboundForwarded = trusted ? join(in.getAll(HttpHeaders.FORWARDED)) : null;
            out.set(HttpHeaders.FORWARDED, inboundForwarded == null ? element.toString() : inboundForwarded + ", " + element);
        }
    }

    private static @Nullable String join(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        return String.join(", ", values);
    }

    /**
     * Format a node (RFC 7239 section 6): IPv6 addresses are bracketed and quoted.
     */
    private static String node(@Nullable String address) {
        if (address == null) {
            return "unknown";
        }
        if (address.indexOf(':') >= 0) {
            return '"' + (address.startsWith("[") ? address : "[" + address + "]") + '"';
        }
        return quoteIfNeeded(address);
    }

    private static String quoteIfNeeded(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!isTokenChar(value.charAt(i))) {
                return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
            }
        }
        return value;
    }

    private static boolean isTokenChar(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    /**
     * Builder for {@link ForwardedHeaders}.
     */
    public static final class Builder {
        private Predicate<? super InetSocketAddress> trustedProxy = address -> false;
        private boolean xForwarded = true;
        private boolean forwarded = true;
        @Nullable
        private String prefix;

        private Builder() {
        }

        /**
         * Which peers are trusted proxies whose forwarding headers are kept and appended to.
         * Defaults to none.
         *
         * @param trustedProxy Tests the address of the peer that sent the inbound request
         * @return This builder
         */
        public Builder trustedProxy(Predicate<? super InetSocketAddress> trustedProxy) {
            this.trustedProxy = Objects.requireNonNull(trustedProxy, "trustedProxy");
            return this;
        }

        /**
         * Whether to write the {@code X-Forwarded-*} headers. Defaults to {@code true}.
         *
         * @param xForwarded Whether to write the headers
         * @return This builder
         */
        public Builder xForwarded(boolean xForwarded) {
            this.xForwarded = xForwarded;
            return this;
        }

        /**
         * Whether to write the RFC 7239 {@code Forwarded} header. Defaults to {@code true}.
         *
         * @param forwarded Whether to write the header
         * @return This builder
         */
        public Builder forwarded(boolean forwarded) {
            this.forwarded = forwarded;
            return this;
        }

        /**
         * The path prefix this hop removed from the request path, written as
         * {@code X-Forwarded-Prefix}. Defaults to none.
         *
         * @param prefix The prefix, e.g. {@code /api}
         * @return This builder
         */
        public Builder prefix(@Nullable String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * @return The forwarded headers writer
         */
        public ForwardedHeaders build() {
            return new ForwardedHeaders(this);
        }
    }
}
