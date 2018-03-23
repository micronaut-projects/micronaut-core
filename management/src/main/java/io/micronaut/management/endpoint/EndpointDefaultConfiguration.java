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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;

/**
 * The default {@link Endpoint} configuration
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties(EndpointDefaultConfiguration.PREFIX)
public class EndpointDefaultConfiguration implements Toggleable {

    /**
     * The prefix for endpoints settings
     */
    public static final String PREFIX = "endpoints.all";

    private boolean enabled = true;
    private boolean sensitive;

    /**
     * @return Whether the endpoint is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Enable the endpoint
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return Does the endpoint expose sensitive information
     */
    public boolean isSensitive() {
        return sensitive;
    }

    /**
     * @param sensitive Define the endpoint as sensitive
     */
    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }
}
