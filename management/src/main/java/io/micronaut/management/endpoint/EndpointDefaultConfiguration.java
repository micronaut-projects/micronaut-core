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
package io.micronaut.management.endpoint;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;

import java.util.Optional;

/**
 * The default {@link io.micronaut.management.endpoint.annotation.Endpoint} configuration.
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties(EndpointDefaultConfiguration.PREFIX)
public class EndpointDefaultConfiguration {

    /**
     * The prefix for endpoints settings.
     */
    public static final String PREFIX = "endpoints.all";

    /**
     * The path for endpoints settings.
     */
    public static final String PATH = "endpoints.all.path";

    /**
     * The path for endpoints settings.
     */
    public static final String PORT = "endpoints.all.port";

    /**
     * The default base path.
     */
    public static final String DEFAULT_ENDPOINT_BASE_PATH = "/";

    private Boolean enabled;
    private Boolean sensitive;
    private Integer port;
    private String path = DEFAULT_ENDPOINT_BASE_PATH;

    /**
     *
     * @return endpoints Base Path (defaults to: {@value #DEFAULT_ENDPOINT_BASE_PATH})
     */
    public String getPath() {
        return path;
    }

    /**
     * @return Whether the endpoint is enabled
     */
    public Optional<Boolean> isEnabled() {
        return Optional.ofNullable(enabled);
    }

    /**
     * @return Does the endpoint expose sensitive information
     */
    public Optional<Boolean> isSensitive() {
        return Optional.ofNullable(sensitive);
    }

    /**
     * Sets whether the endpoint is enabled.
     *
     * @param enabled True it is enabled, null for the default behaviour
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets whether the endpoint is sensitive.
     *
     * @param sensitive True it is sensitive, null for the default behaviour
     */
    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    /**
     * The endpoints base path. Default value ({@value #DEFAULT_ENDPOINT_BASE_PATH}).
     * @param path The path
     */
    public void setPath(String path) {
        if (StringUtils.isNotEmpty(path)) {
            this.path = path;
        }
    }

    /**
     * @return The port to expose endpoints via.
     */
    public Optional<Integer> getPort() {
        return Optional.ofNullable(port);
    }

    /**
     * Sets the port to expose endpoints via.
     * @param port The port
     */
    public void setPort(Integer port) {
        this.port = port;
    }
}
