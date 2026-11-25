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
    ProxyHeaderParser(HttpRequest request, boolean allowForwarded, boolean allowXForwarded) {
        HttpHeaders headers = request.getHeaders();
        if (allowForwarded && headers.contains(HttpHeaders.FORWARDED)) {
            parseForwarded(headers.getAll(HttpHeaders.FORWARDED));
        } else if (allowXForwarded) {
            forwardedProto = StringUtils.trimToNull(first(headers.get(X_FORWARDED_PROTO)));
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
     * Reads the {@code Forwarded} elements. The host, protocol and port are taken together from the
     * first element with a valid {@code host}, so that they describe the same hop. Without such an
     * element, only the first {@code proto} is kept.
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
                    if (key.equalsIgnoreCase(FOR)) {
                        forwardedFor.add(value);
                    } else if (key.equalsIgnoreCase(BY)) {
                        if (forwardedBy == null) {
                            forwardedBy = value;
                        }
                    } else if (key.equalsIgnoreCase(PROTO)) {
                        if (elementProto == null) {
                            elementProto = value;
                        }
                    } else if (key.equalsIgnoreCase(HOST)) {
                        if (elementHost == null) {
                            elementHost = value;
                        }
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
                        forwardedProto = elementProto;
                    }
                }
            }
        }
        if (!hostFound) {
            forwardedProto = firstProto;
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
     * @return The host
     */
    @Nullable
    public String getHost() {
        return forwardedHost;
    }

    /**
     * @return The scheme or protocol
     */
    @Nullable
    public String getScheme() {
        return forwardedProto;
    }

    /**
     * @return The port
     */
    @Nullable
    public Integer getPort() {
        return forwardedPort;
    }

    @Nullable
    private HostAndPort parseHostAndPort(String value) {
        if (value.startsWith("[")) {
            // IPv6
            int closingBracketIndex = value.indexOf(']');
            if (closingBracketIndex == -1) {
                return null;
            }
            String host = value.substring(0, closingBracketIndex + 1);
            if (closingBracketIndex == value.length() - 1) {
                return new HostAndPort(host, null);
            } else if (value.charAt(closingBracketIndex + 1) == ':') {
                Integer port = parsePort(value.substring(closingBracketIndex + 2));
                if (port != null) {
                    return new HostAndPort(host, port);
                }
            }
            return null;
        } else {
            // IPv4 or name
            int portSeparatorIndex = value.lastIndexOf(':');
            if (portSeparatorIndex == -1) {
                return new HostAndPort(value, null);
            }
            if (value.indexOf(':') != portSeparatorIndex) {
                return null;
            }
            Integer port = parsePort(value.substring(portSeparatorIndex + 1));
            if (port != null) {
                return new HostAndPort(value.substring(0, portSeparatorIndex), port);
            }
            return null;
        }
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

    @Nullable
    private Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 0 && port <= 65535) {
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
