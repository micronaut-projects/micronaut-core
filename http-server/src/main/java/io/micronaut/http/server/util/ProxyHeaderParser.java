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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private String forwardedBy = null;
    private String forwardedHost = null;
    private String forwardedProto = null;
    private Integer forwardedPort = null;

    /**
     * @param request The request
     */
    public ProxyHeaderParser(HttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        if (headers.contains(HttpHeaders.FORWARDED)) {
            headers.getAll(HttpHeaders.FORWARDED)
                    .stream()
                    .flatMap(header -> {
                        if (header.contains(ELEMENT_DELIMITER)) {
                            return Arrays.stream(header.split(ELEMENT_DELIMITER));
                        } else {
                            return Stream.of(header);
                        }
                    }).forEach(forwarded -> {
                        String[] params = forwarded.split(PARAM_DELIMITER);
                        for (String param: params) {
                            String[] parts = param.split(PAIR_DELIMITER);
                            if (parts.length == 2) {
                                String key = parts[0].trim();
                                String value = trimQuotes(parts[1].trim());
                                if (key.equalsIgnoreCase(FOR)) {
                                    forwardedFor.add(value);
                                } else if (key.equalsIgnoreCase(BY) && forwardedBy == null) {
                                    forwardedBy = value;
                                } else if (key.equalsIgnoreCase(PROTO) && forwardedProto == null) {
                                    forwardedProto = value;
                                } else if (key.equalsIgnoreCase(HOST) && forwardedHost == null) {
                                    if (value.contains(":")) {
                                        String[] host = value.split(":");
                                        forwardedHost = host[0];
                                        forwardedPort = Integer.valueOf(host[1]);
                                    } else {
                                        forwardedHost = value;
                                    }
                                }
                            }
                        }
                    });
        } else {
            forwardedProto = StringUtils.trimToNull(headers.get(X_FORWARDED_PROTO));
            forwardedHost = headers.get(X_FORWARDED_HOST);
            try {
                if (forwardedHost != null && forwardedHost.contains(":")) {
                    String[] parts = forwardedHost.split(":");
                    forwardedHost = parts[0];
                    forwardedPort = Integer.valueOf(parts[1]);
                } else {
                    String portHeader = headers.get(X_FORWARDED_PORT);
                    if (portHeader != null) {
                        forwardedPort = Integer.valueOf(portHeader);
                    }
                }
            } catch (NumberFormatException ignored) { }
            String forwardedForHeader = headers.get(X_FORWARDED_FOR);
            if (forwardedForHeader != null) {
                forwardedFor = Arrays.stream(forwardedForHeader.split(ELEMENT_DELIMITER))
                        .map(String::trim)
                        .collect(Collectors.toList());
            }
        }
    }

    /**
     * @return The client addresses
     */
    @NonNull
    public List<String> getFor() {
        return forwardedFor;
    }

    /**
     * @return The proxy
     */
    public String getBy() {
        return forwardedBy;
    }

    /**
     * @return The host
     */
    public String getHost() {
        return forwardedHost;
    }

    /**
     * @return The scheme or protocol
     */
    public String getScheme() {
        return forwardedProto;
    }

    /**
     * @return The port
     */
    public Integer getPort() {
        return forwardedPort;
    }

    private String trimQuotes(String value) {
        if (value != null && value.startsWith("\"")) {
            return value.substring(1, value.length() - 1);
        } else {
            return value;
        }
    }

}
