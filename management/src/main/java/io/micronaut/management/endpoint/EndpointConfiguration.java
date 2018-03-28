/*
 * Copyright 2017 original authors
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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.util.Toggleable;

import java.util.Optional;

/**
 * An {@link Endpoint} configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachProperty(EndpointConfiguration.PREFIX)
public class EndpointConfiguration {

    public static final String PREFIX = "endpoints";

    private final String id;
    protected Optional<Boolean> enabled = Optional.empty();
    protected Optional<Boolean> sensitive = Optional.empty();

    private EndpointDefaultConfiguration defaultConfiguration;

    public EndpointConfiguration(@Parameter String id, EndpointDefaultConfiguration defaultConfiguration) {
        this.id = id;
        this.defaultConfiguration = defaultConfiguration;
    }

    /**
     * @return The ID of the endpoint
     * @see Endpoint#value()
     */
    public String getId() {
        return id;
    }

    /**
     * @return Is the endpoint enabled. If not present, use the value of {@link Endpoint#defaultEnabled()}
     */
    public Optional<Boolean> isEnabled() {
        if (enabled.isPresent()) {
            return enabled;
        }
        return defaultConfiguration.isEnabled();
    }

    /**
     * @return Does the endpoint expose sensitive information. If not present, use the value of {@link Endpoint#defaultSensitive()}
     */
    public Optional<Boolean> isSensitive() {
        if (sensitive.isPresent()) {
            return sensitive;
        }
        return defaultConfiguration.isSensitive();
    }
}
