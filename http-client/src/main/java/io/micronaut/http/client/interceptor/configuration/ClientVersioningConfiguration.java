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
package io.micronaut.http.client.interceptor.configuration;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.http.client.DefaultHttpClientConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * A base configuration class for configuring {@link io.micronaut.http.client.annotation.Client} versioning.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
public class ClientVersioningConfiguration {

    /**
     * The prefix for versioning configuration.
     */
    public static final String PREFIX = DefaultHttpClientConfiguration.PREFIX + ".versioning";

    /**
     * The default configuration property name.
     */
    public static final String DEFAULT = "default";

    /**
     * List of request header names to specify version.
     */
    private List<String> headerNames = new ArrayList<>();

    /**
     * List of request query parameter names to specify version.
     */
    private List<String> parameterNames = new ArrayList<>();

    /**
     * The id of the {@link io.micronaut.http.client.annotation.Client} to apply the versioning configuration for.
     */
    private final String clientName;

    /**
     * Creates a new configuration for the given client ID.
     *
     * @param clientName ID of the {@link io.micronaut.http.client.annotation.Client} to apply configuration for.
     */
    ClientVersioningConfiguration(@Parameter String clientName) {
        this.clientName = clientName;
    }

    /**
     * @return The ID of the client to apply the versioning for.
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * @return The list of request header names.
     */
    public List<String> getHeaders() {
        return headerNames;
    }

    /**
     * @return The list of request query parameter names.
     */
    public List<String> getParameters() {
        return parameterNames;
    }

    /**
     * @param headerNames The list of request header names.
     */
    public void setHeaders(List<String> headerNames) {
        this.headerNames = headerNames;
    }

    /**
     * @param parameterNames The list of request query parameter names.
     */
    public void setParameters(List<String> parameterNames) {
        this.parameterNames = parameterNames;
    }

}
