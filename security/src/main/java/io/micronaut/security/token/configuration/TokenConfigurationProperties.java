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

package io.micronaut.security.token.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.config.SecurityConfiguration;

/**
 * Retrieves configuration for creating the JWT token
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenConfigurationProperties.PREFIX)
public class TokenConfigurationProperties implements TokenConfiguration {

    public static final String PREFIX = SecurityConfiguration.PREFIX + ".token";

    private static final String DEFAULT_ROLES_CLAIM_NAME = "roles";
    private static final Integer DEFAULT_EXPIRATION = 3600;

    protected Integer refreshTokenExpiration = null;
    protected Integer accessTokenExpiration = DEFAULT_EXPIRATION;
    protected String rolesClaimName = DEFAULT_ROLES_CLAIM_NAME;


    @Override
    public Integer getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    /**
     * @see TokenConfiguration#getAccessTokenExpiration().
     * If not specified, defaults to {@link #DEFAULT_EXPIRATION}.
     */
    @Override
    public Integer getAccessTokenExpiration() {
        return accessTokenExpiration;
    }


    /**
     * @see TokenConfiguration#getRolesClaimName() ().
     * If not specified, defaults to {@link #DEFAULT_ROLES_CLAIM_NAME}.
     */
    @Override
    public String getRolesClaimName() {
        return rolesClaimName;
    }
}
