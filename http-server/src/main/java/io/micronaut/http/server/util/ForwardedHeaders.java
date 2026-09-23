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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *     {@code -Prefix} values the first proxy set are kept. If the trusted chain only comes in
 *     one of the two formats, it is translated to the other one before this hop is appended
 *     (RFC 7239 section 7.4), so that both headers describe the same chain: a downstream server
 *     that prefers {@code Forwarded} must not see this hop as the client.</li>
 *     <li>From any other peer, the inbound values could be forged, so they are replaced with the
 *     values of this hop.</li>
 * </ul>
 * By default no peer is trusted.
 * <p>The trust predicate only evaluates the immediate peer, i.e. the remote address of the
 * connection the inbound request arrived on. The addresses inside the inbound headers are never
 * tested. With a chain of proxies in front of this server, e.g. two, trust the nearer one, and
 * rely on it having applied the same rule to its own peer.
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
    private static final String UNKNOWN = "unknown";

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

        // read the inbound values before the outbound headers change: the two requests can share
        // their headers, e.g. for a request mutated from a Netty server request
        String inboundFor = trusted ? join(in.getAll(X_FORWARDED_FOR)) : null;
        String inboundProto = trusted ? in.get(X_FORWARDED_PROTO) : null;
        String inboundHost = trusted ? in.get(X_FORWARDED_HOST) : null;
        String inboundPort = trusted ? in.get(X_FORWARDED_PORT) : null;
        String inboundPrefix = trusted ? in.get(X_FORWARDED_PREFIX) : null;
        String inboundForwarded = trusted ? join(in.getAll(HttpHeaders.FORWARDED)) : null;
        if (trusted) {
            boolean hasXForwarded = inboundFor != null || inboundProto != null || inboundHost != null || inboundPort != null;
            if (inboundForwarded == null && hasXForwarded) {
                // keep the chain when this hop is appended to a Forwarded header of its own
                inboundForwarded = toForwarded(inboundFor, inboundProto, inboundHost, inboundPort);
            } else if (inboundForwarded != null && !hasXForwarded) {
                // keep the chain when this hop is appended to X-Forwarded-* headers of its own
                List<Map<String, String>> elements = parseForwarded(inboundForwarded);
                List<String> addresses = new ArrayList<>(elements.size());
                String firstHost = null;
                for (Map<String, String> element : elements) {
                    String address = element.get("for");
                    addresses.add(address == null ? UNKNOWN : toXForwardedFor(address));
                    if (inboundProto == null) {
                        inboundProto = element.get("proto");
                    }
                    if (firstHost == null) {
                        firstHost = element.get("host");
                    }
                }
                inboundFor = addresses.isEmpty() ? null : String.join(", ", addresses);
                if (firstHost != null) {
                    int portSeparator = portSeparator(firstHost);
                    if (portSeparator >= 0) {
                        inboundHost = firstHost.substring(0, portSeparator);
                        inboundPort = firstHost.substring(portSeparator + 1);
                    } else {
                        inboundHost = firstHost;
                        Integer defaultPort = defaultPort(inboundProto);
                        inboundPort = defaultPort == null ? null : String.valueOf(defaultPort);
                    }
                }
            }
        }

        for (String name : X_FORWARDED_HEADERS) {
            out.remove(name);
        }
        out.remove(HttpHeaders.FORWARDED);

        if (xForwarded) {
            String forwardedFor = clientAddress == null ? "unknown" : clientAddress;
            out.set(X_FORWARDED_FOR, inboundFor == null ? forwardedFor : inboundFor + ", " + forwardedFor);
            out.set(X_FORWARDED_PROTO, inboundProto == null ? proto : inboundProto);
            if (inboundHost != null) {
                out.set(X_FORWARDED_HOST, inboundHost);
            } else if (host != null) {
                out.set(X_FORWARDED_HOST, host);
            }
            out.set(X_FORWARDED_PORT, inboundPort == null ? String.valueOf(port) : inboundPort);
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
            out.set(HttpHeaders.FORWARDED, inboundForwarded == null ? element.toString() : inboundForwarded + ", " + element);
        }
    }

    /**
     * Translate a chain of {@code X-Forwarded-*} headers to {@code Forwarded} elements: one per
     * {@code X-Forwarded-For} address, the first one also carrying the scheme and host the first
     * proxy received.
     */
    private static String toForwarded(@Nullable String forwardedFor, @Nullable String proto, @Nullable String host, @Nullable String port) {
        List<String> elements = new ArrayList<>();
        if (forwardedFor != null) {
            for (String address : forwardedFor.split(",", -1)) {
                String trimmed = address.trim();
                elements.add("for=" + forwardedNode(trimmed.isEmpty() ? UNKNOWN : trimmed));
            }
        }
        StringBuilder first = new StringBuilder(elements.isEmpty() ? "" : elements.get(0));
        if (proto != null) {
            first.append(first.isEmpty() ? "" : ";").append("proto=").append(quoteIfNeeded(proto.trim()));
        }
        if (host != null) {
            String hostWithPort = host.trim();
            if (port != null && portSeparator(hostWithPort) < 0 && !port.trim().equals(String.valueOf(defaultPort(proto)))) {
                hostWithPort = hostWithPort + ":" + port.trim();
            }
            first.append(first.isEmpty() ? "" : ";").append("host=").append(quoteIfNeeded(hostWithPort));
        }
        if (elements.isEmpty()) {
            elements.add(first.toString());
        } else {
            elements.set(0, first.toString());
        }
        return String.join(", ", elements);
    }

    /**
     * Format an {@code X-Forwarded-For} address as a {@code Forwarded} node: an IPv6 address is
     * bracketed, and a value that is no token (e.g. with a port) is quoted.
     */
    private static String forwardedNode(String address) {
        if (!address.startsWith("[") && address.indexOf(':') != address.lastIndexOf(':')) {
            return '"' + "[" + address + "]" + '"';
        }
        return quoteIfNeeded(address);
    }

    /**
     * Format a {@code Forwarded} node as an {@code X-Forwarded-For} address: the brackets of an
     * IPv6 address without a port are removed.
     */
    private static String toXForwardedFor(String node) {
        if (node.startsWith("[") && node.endsWith("]")) {
            return node.substring(1, node.length() - 1);
        }
        return node;
    }

    /**
     * Parse the elements of a {@code Forwarded} header (RFC 7239 section 4). Parameter names are
     * lower case, quoted values are unquoted.
     */
    private static List<Map<String, String>> parseForwarded(String header) {
        List<Map<String, String>> elements = new ArrayList<>();
        Map<String, String> element = new LinkedHashMap<>();
        StringBuilder pair = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < header.length()) {
                    pair.append(c).append(header.charAt(++i));
                    continue;
                }
                if (c == '"') {
                    quoted = false;
                }
                pair.append(c);
            } else if (c == '"') {
                quoted = true;
                pair.append(c);
            } else if (c == ';' || c == ',') {
                addPair(element, pair);
                if (c == ',') {
                    if (!element.isEmpty()) {
                        elements.add(element);
                    }
                    element = new LinkedHashMap<>();
                }
            } else {
                pair.append(c);
            }
        }
        addPair(element, pair);
        if (!element.isEmpty()) {
            elements.add(element);
        }
        return elements;
    }

    private static void addPair(Map<String, String> element, StringBuilder pair) {
        String text = pair.toString().trim();
        pair.setLength(0);
        int separator = text.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String name = text.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = text.substring(separator + 1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            StringBuilder unquoted = new StringBuilder(value.length());
            for (int i = 1; i < value.length() - 1; i++) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length() - 1) {
                    c = value.charAt(++i);
                }
                unquoted.append(c);
            }
            value = unquoted.toString();
        }
        element.putIfAbsent(name, value);
    }

    /**
     * @return The index of the separator of the port of a host, or {@code -1} if it has none
     */
    private static int portSeparator(String host) {
        int portSeparator = host.lastIndexOf(':');
        if (portSeparator > host.lastIndexOf(']') && host.indexOf(':') == portSeparator) {
            return portSeparator;
        }
        if (host.startsWith("[") && portSeparator > host.lastIndexOf(']')) {
            return portSeparator;
        }
        return -1;
    }

    private static @Nullable Integer defaultPort(@Nullable String proto) {
        if (proto == null) {
            return null;
        }
        return switch (proto.trim().toLowerCase(Locale.ROOT)) {
            case "http", "ws" -> HTTP_PORT;
            case "https", "wss" -> HTTPS_PORT;
            default -> null;
        };
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
         * Defaults to none. Only the immediate peer (the remote address of the connection) is
         * tested, not the addresses in the forwarding headers: with a chain of proxies, trust
         * the nearest one, which must apply the same rule to its own peer.
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
