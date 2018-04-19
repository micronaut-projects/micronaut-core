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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.security.config.SecurityConfiguration;
import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;

/**
 * Stores configuration for JWT
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
    private JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;

    @NotBlank @Size()
    protected String secret;

    public Integer getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public Integer getDefaultExpiration() {
        return defaultExpiration;
    }

    public String getRolesClaimName() {
        return rolesClaimName;
    }

    public String getSecret() {
        return this.secret;
    }

    public JWSAlgorithm getJwsAlgorithm() { return jwsAlgorithm; }
}
