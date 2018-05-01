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

package io.micronaut.security.token.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.config.SecurityConfigurationProperties;

/**
 * Defines Security Token Configuration.
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenConfigurationProperties.PREFIX)
public class TokenConfigurationProperties implements TokenConfiguration {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".token";

    private static final String DEFAULT_ROLES_NAME = "roles";
    protected boolean enabled = true;
    protected String rolesName = DEFAULT_ROLES_NAME;

    @Override
    public boolean isEnabled() {
        return enabled;
    }


    /**
     * @see TokenConfiguration#getRolesName() ().
     * If not specified, defaults to {@link #DEFAULT_ROLES_NAME}.
     */
    @Override
    public String getRolesName() {
        return rolesName;
    }
}
