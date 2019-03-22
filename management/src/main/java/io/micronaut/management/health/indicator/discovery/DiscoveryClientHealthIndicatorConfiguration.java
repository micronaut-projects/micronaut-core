/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.management.health.indicator.discovery;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import static io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicatorConfiguration.PREFIX;

/**
 * Enables the user to enable or disable the health indicator.
 *
 * @author rvanderwerf
 * @since 1.1.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
public class DiscoveryClientHealthIndicatorConfiguration implements Toggleable {

    static final String PREFIX = "discoveryClient.indicator";

    private boolean enabled;

    /**
     * @return {@code true} If health indicator should be enabled.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * If health indicator should be enabled.
     *
     * @param enabled True If health indicator should be enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
