/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import javax.annotation.Nonnull;
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
            for (String header: headers.getAll(HttpHeaders.FORWARDED)) {
                header = StringUtils.trimToNull(header);

                while (StringUtils.isNotEmpty(header)) {
                    int parameterEnd = header.indexOf(PARAM_DELIMITER);
                    String parameter;
                    if (parameterEnd > -1) {
                        parameter = header.substring(0, parameterEnd);
                        header = header.substring(parameterEnd + 1);
                    } else {
                        parameter = header;
                        header = "";
                    }

                    int firstPair = parameter.indexOf(PAIR_DELIMITER);
                    String pairName = parameter.substring(0, firstPair);

                    if (pairName.equalsIgnoreCase(FOR)) {
                        processFor(parameter);
                    } else if (pairName.equalsIgnoreCase(BY)) {
                        processBy(parameter);
                    } else if (pairName.equalsIgnoreCase(PROTO)) {
                        processProto(parameter);
                    } else if (pairName.equalsIgnoreCase(HOST)) {
                        processHost(parameter);
                    }
                }
            }
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
    @Nonnull
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

    private void processFor(String parameter) {
        forwardedFor.addAll(Arrays.stream(parameter.split(ELEMENT_DELIMITER))
                .map(pair -> pair.split(PAIR_DELIMITER))
                .filter(pair -> pair.length == 2)
                .map(pair -> pair[1])
                .map(String::trim)
                .map(value -> {
                    if (value.startsWith("\"")) {
                        return value.substring(1, value.length() - 1);
                    } else {
                        return value;
                    }
                })
                .collect(Collectors.toList()));
    }

    private void processBy(String parameter) {
        forwardedBy = processSimpleParameter(parameter);
    }

    private void processProto(String parameter) {
        forwardedProto = processSimpleParameter(parameter);
    }

    private void processHost(String parameter) {
        forwardedHost = processSimpleParameter(parameter);
    }

    private String processSimpleParameter(String parameter) {
        String[] pair = parameter.split(PAIR_DELIMITER);
        if (pair.length == 2) {
            String value =  pair[1];
            if (value.startsWith("\"")) {
                return value.substring(1, value.length() - 1);
            } else {
                return value;
            }
        } else {
            return null;
        }
    }

}
