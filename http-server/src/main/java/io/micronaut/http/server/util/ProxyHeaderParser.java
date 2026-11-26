/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Responsible for parsing and returning the information
 * stored in the standard and de facto standard proxy headers.
 *
 * @author James Kleeh
 * @since 1.2.0
 */
public class ProxyHeaderParser {

    private static final String FOR = "for";
    private static final String BY = "by";
    private static final String HOST = "host";
    private static final String PROTO = "proto";

    private static final String PARAM_DELIMITER = ";";
    private static final String ELEMENT_DELIMITER = ",";
    private static final String PAIR_DELIMITER = "=";

    private static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_FORWARDED_PORT = "X-Forwarded-Port";

    private List<String> forwardedFor = new ArrayList<>();
    @Nullable
    private String forwardedBy = null;
    @Nullable
    private String forwardedHost = null;
    @Nullable
    private String forwardedProto = null;
    @Nullable
    private Integer forwardedPort = null;
    @Nullable
    private String hostProto = null;

    /**
     * Parses the {@code Forwarded} header or, when it is absent, the {@code X-Forwarded-*} headers.
     * This constructor reads both header families whatever the values of
     * {@code micronaut.server.host-resolution.allow-forwarded} and {@code allow-xforwarded}: those settings
     * apply to host resolution by {@link DefaultHttpHostResolver} only.
     *
     * @param request The request
     */
    public ProxyHeaderParser(HttpRequest request) {
        this(request, true, true);
    }

    /**
     * Parses the proxy header families that are allowed. When both are allowed and a {@code Forwarded}
     * header is present, the {@code X-Forwarded-*} headers are ignored, as with {@link #ProxyHeaderParser(HttpRequest)}.
     * When the {@code Forwarded} header is not allowed, the {@code X-Forwarded-*} headers are read even if a
     * {@code Forwarded} header is present. A family that is not allowed contributes no value, including {@link #getFor()}.
     * Package-private: it backs the host resolution switches of {@link DefaultHttpHostResolver} and is not public API.
     *
     * @param request The request
     * @param allowForwarded Whether the {@code Forwarded} header is read
     * @param allowXForwarded Whether the {@code X-Forwarded-Proto}, {@code X-Forwarded-Host}, {@code X-Forwarded-Port} and {@code X-Forwarded-For} headers are read
     */
    ProxyHeaderParser(HttpRequest<?> request, boolean allowForwarded, boolean allowXForwarded) {
        HttpHeaders headers = request.getHeaders();
        if (allowForwarded && headers.contains(HttpHeaders.FORWARDED)) {
            parseForwarded(headers.getAll(HttpHeaders.FORWARDED));
        } else if (allowXForwarded) {
            forwardedProto = StringUtils.trimToNull(first(headers.get(X_FORWARDED_PROTO)));
            hostProto = forwardedProto;
            String forwardedHostHeader = StringUtils.trimToNull(first(headers.get(X_FORWARDED_HOST)));
            if (forwardedHostHeader != null) {
                HostAndPort hostAndPort = parseHostAndPort(forwardedHostHeader);
                if (hostAndPort != null) {
                    forwardedHost = hostAndPort.host();
                    forwardedPort = hostAndPort.port();
                }
            }
            if (forwardedPort == null) {
                String portHeader = StringUtils.trimToNull(first(headers.get(X_FORWARDED_PORT)));
                if (portHeader != null) {
                    forwardedPort = parsePort(portHeader);
                }
            }
            String forwardedForHeader = headers.get(X_FORWARDED_FOR);
            if (forwardedForHeader != null) {
                forwardedFor = Arrays.stream(forwardedForHeader.split(ELEMENT_DELIMITER))
                        .map(String::trim)
                        .collect(Collectors.toList());
            }
        }
    }

    /**
     * Reads the {@code Forwarded} elements. The host and port come from the first element whose
     * {@code host} splits into a host and an optional port in the range 1 to 65535; an element whose
     * {@code host} does not is skipped. Whether the host is a valid URI authority is not checked here:
     * {@link DefaultHttpHostResolver} checks it and, when it is not, ignores the proxy headers altogether
     * rather than moving on to the next element. {@link #getScheme()} keeps the first {@code proto} of the
     * header, while {@link #getHostScheme()} gives the {@code proto} of the element that supplied the host,
     * so that host, protocol and port describe the same hop.
     *
     * @param headerValues The values of the {@code Forwarded} header
     */
    private void parseForwarded(List<String> headerValues) {
        boolean hostFound = false;
        String firstProto = null;
        for (String header : headerValues) {
            for (String element : header.split(ELEMENT_DELIMITER)) {
                String elementHost = null;
                String elementProto = null;
                for (String param : element.split(PARAM_DELIMITER)) {
                    String[] parts = param.split(PAIR_DELIMITER);
                    if (parts.length != 2) {
                        continue;
                    }
                    String key = parts[0].trim();
                    String value = trimQuotes(parts[1].trim());
                    // the first by of the header, and the first proto and host of the element, are kept
                    if (key.equalsIgnoreCase(FOR)) {
                        forwardedFor.add(value);
                    } else if (key.equalsIgnoreCase(BY) && forwardedBy == null) {
                        forwardedBy = value;
                    } else if (key.equalsIgnoreCase(PROTO) && elementProto == null) {
                        elementProto = value;
                    } else if (key.equalsIgnoreCase(HOST) && elementHost == null) {
                        elementHost = value;
                    }
                }
                if (firstProto == null) {
                    firstProto = elementProto;
                }
                if (!hostFound && elementHost != null) {
                    HostAndPort hostAndPort = parseHostAndPort(elementHost);
                    if (hostAndPort != null) {
                        hostFound = true;
                        forwardedHost = hostAndPort.host();
                        forwardedPort = hostAndPort.port();
                        hostProto = elementProto;
                    }
                }
            }
        }
        forwardedProto = firstProto;
        if (!hostFound) {
            hostProto = firstProto;
        }
    }

    /**
     * @return The client addresses
     */
    public List<String> getFor() {
        return forwardedFor;
    }

    /**
     * @return The proxy
     */
    @Nullable
    public String getBy() {
        return forwardedBy;
    }

    /**
     * @return The host. For the {@code Forwarded} header, it comes from the first element whose {@code host}
     * parses, which may not be the element that {@link #getScheme()} comes from
     */
    @Nullable
    public String getHost() {
        return forwardedHost;
    }

    /**
     * @return The scheme or protocol. For the {@code Forwarded} header, it is the first {@code proto} of the header,
     * which may come from another element than {@link #getHost()}; host resolution pairs the host with the
     * {@code proto} of its own element
     */
    @Nullable
    public String getScheme() {
        return forwardedProto;
    }

    /**
     * @return The scheme or protocol that goes with {@link #getHost()}: for the {@code Forwarded} header, the
     * {@code proto} of the element that supplied the host, which may be absent although another element has one
     */
    @Nullable
    String getHostScheme() {
        return hostProto;
    }

    /**
     * @return The port, a number from 1 to 65535, or {@code null}
     */
    @Nullable
    public Integer getPort() {
        return forwardedPort;
    }

    @Nullable
    private HostAndPort parseHostAndPort(String value) {
        String host;
        String portText;
        if (value.startsWith("[")) {
            // IPv6
            int closingBracketIndex = value.indexOf(']');
            if (closingBracketIndex == -1) {
                return null;
            }
            host = value.substring(0, closingBracketIndex + 1);
            if (closingBracketIndex == value.length() - 1) {
                portText = null;
            } else if (value.charAt(closingBracketIndex + 1) == ':') {
                portText = value.substring(closingBracketIndex + 2);
            } else {
                return null;
            }
        } else {
            // IPv4 or name
            int portSeparatorIndex = value.lastIndexOf(':');
            if (portSeparatorIndex == -1) {
                host = value;
                portText = null;
            } else if (value.indexOf(':') != portSeparatorIndex) {
                return null;
            } else {
                host = value.substring(0, portSeparatorIndex);
                portText = value.substring(portSeparatorIndex + 1);
            }
        }
        if (portText == null) {
            return new HostAndPort(host, null);
        }
        Integer port = parsePort(portText);
        return port == null ? null : new HostAndPort(host, port);
    }

    /**
     * @param list A comma separated list, one value per proxy
     * @return The first value of the list
     */
    @Nullable
    private static String first(@Nullable String list) {
        if (list == null) {
            return null;
        }
        int comma = list.indexOf(',');
        return comma < 0 ? list : list.substring(0, comma);
    }

    /**
     * @param value The port text
     * @return The port, or {@code null} when it is not a number from 1 to 65535. Port 0 is rejected with the
     * other out-of-range ports: no client can connect to it
     */
    @Nullable
    private Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
            // Fall through
        }
        return null;
    }

    private String trimQuotes(String value) {
        // Only unwrap a balanced quoted-string (RFC 7239 section 4). A value that merely opens with
        // a quote is left untouched: stripping the last character of "abc would drop a real char, and
        // a lone " would make substring(1, 0) throw out of the constructor on the Forwarded header.
        if (value != null && value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        } else {
            return value;
        }
    }

    private record HostAndPort(String host, @Nullable Integer port) {
    }

}
