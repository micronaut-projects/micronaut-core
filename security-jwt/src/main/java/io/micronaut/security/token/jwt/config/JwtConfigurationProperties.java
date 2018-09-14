/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security.token.jwt.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.config.TokenConfigurationProperties;

/**
 * {@link JwtConfiguration} implementation.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = TokenConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@ConfigurationProperties(JwtConfigurationProperties.PREFIX)
public class JwtConfigurationProperties implements JwtConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".jwt";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    private boolean enabled = DEFAULT_ENABLED;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether JWT security is enabled. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled True if it is
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
