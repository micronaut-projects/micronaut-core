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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import java.util.Optional;

/**
 * An {@link io.micronaut.management.endpoint.annotation.Endpoint} configuration.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachProperty(EndpointConfiguration.PREFIX)
public class EndpointConfiguration {

    /**
     * The prefix for endpoints configurations.
     */
    public static final String PREFIX = "endpoints";

    private Boolean enabled;
    private Boolean sensitive;

    private final String id;
    private EndpointDefaultConfiguration defaultConfiguration;

    /**
     * @param id The id of the endpoint
     * @param defaultConfiguration The default endpoint configuration
     */
    public EndpointConfiguration(@Parameter String id, EndpointDefaultConfiguration defaultConfiguration) {
        this.id = id;
        this.defaultConfiguration = defaultConfiguration;
    }

    /**
     * @return The ID of the endpoint
     * @see io.micronaut.management.endpoint.annotation.Endpoint#value()
     */
    public String getId() {
        return id;
    }

    /**
     * @return Is the endpoint enabled. If not present, use the value of {@link io.micronaut.management.endpoint.annotation.Endpoint#defaultEnabled()}
     */
    public Optional<Boolean> isEnabled() {
        if (enabled != null) {
            return Optional.of(enabled);
        }
        return defaultConfiguration.isEnabled();
    }

    /**
     * @return Does the endpoint expose sensitive information. If not present, use the value of {@link io.micronaut.management.endpoint.annotation.Endpoint#defaultSensitive()}
     */
    public Optional<Boolean> isSensitive() {
        if (sensitive != null) {
            return Optional.of(sensitive);
        }
        return defaultConfiguration.isSensitive();
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
}
