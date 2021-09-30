/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.management.endpoint;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} implementation of {@link io.micronaut.management.endpoint.EndpointsFilterConfiguration}.
 * @author Sergio del Amo
 * @since 3.1.0
 */
@ConfigurationProperties(EndpointsFilterConfigurationProperties.PREFIX)
public class EndpointsFilterConfigurationProperties implements EndpointsFilterConfiguration {
    /**
     * The prefix for the {@link EndpointsFilter} configuration
     */
    public static final String PREFIX = "endpoints.filter";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * @return Whether the {@link EndpointsFilter} is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable or disable the {@link EndpointsFilter}
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
