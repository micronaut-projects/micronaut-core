/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.management.health.indicator.service;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import static io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicatorConfiguration.PREFIX;

/**
 * @author Sergio del Amo
 * @since 3.8.6
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", notEquals = StringUtils.FALSE)
public class ServiceReadyHealthIndicatorConfiguration implements Toggleable {

    static final String PREFIX = "endpoints.health.service";
    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;

    /**
     * Health indicator is enabled. Default is true.
     * @return {@code true} If health indicator should be enabled. Default is true.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * If service health indicator should be enabled. Default is {@value #DEFAULT_ENABLED}.
     *
     * @param enabled True If service health indicator should be enabled. Default is {@value #DEFAULT_ENABLED}.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
