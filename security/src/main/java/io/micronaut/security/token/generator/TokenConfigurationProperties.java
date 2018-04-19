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

package io.micronaut.security.token.generator;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.config.SecurityConfiguration;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Stores configuration for JWT.
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
    protected Integer defaultExpiration = DEFAULT_EXPIRATION;
    protected String rolesClaimName = DEFAULT_ROLES_CLAIM_NAME;
    protected JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;

    @NotBlank @Size()
    protected String secret;

    /**
     * refreshTokenExpiration getter.
     * @return expiration time in milliseconds
     */
    public Integer getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    /**
     * defaultExpiration getter.
     * @return expiration time in milliseconds
     */
    public Integer getDefaultExpiration() {
        return defaultExpiration;
    }

    /**
     * rolesClaimName getter.
     * @return e.g. roles
     */
    public String getRolesClaimName() {
        return rolesClaimName;
    }

    /**
     * secret getter.
     * @return secret used to sign the JWT
     */
    public String getSecret() {
        return this.secret;
    }

    /**
     * jwsAlgorithm getter.
     * @return a JWSAlgorithm
     */
    public JWSAlgorithm getJwsAlgorithm() {
        return jwsAlgorithm;
    }
}
