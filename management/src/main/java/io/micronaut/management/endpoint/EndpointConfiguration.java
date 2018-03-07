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
public class EndpointConfiguration implements Toggleable {

    public static final String PREFIX = "endpoints";

    private final String id;
    private Optional<Boolean> enabled = Optional.empty();
    private Optional<Boolean> sensitive = Optional.empty();

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

    @Override
    public boolean isEnabled() {
        return enabled.orElseGet(()->defaultConfiguration.isEnabled());
    }

    /**
     * @return Does the endpoint expose sensitive information
     */
    public boolean isSensitive() {
        return sensitive.orElseGet(()->defaultConfiguration.isSensitive());
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = Optional.ofNullable(enabled);
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = Optional.ofNullable(sensitive);
    }
}
