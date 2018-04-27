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

package io.micronaut.security.token.jwt.signature;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.config.CryptoAlgorithm;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;
import io.micronaut.security.token.jwt.config.JwtGeneratorConfigurationProperties;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = JwtConfigurationProperties.PREFIX + ".enabled")
@ConfigurationProperties(JwtGeneratorSignatureConfigurationProperties.PREFIX)
public class JwtGeneratorSignatureConfigurationProperties implements JwtGeneratorSignatureConfiguration {
    public static final String PREFIX = JwtGeneratorConfigurationProperties.PREFIX + ".signature";

    protected JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    protected CryptoAlgorithm type = CryptoAlgorithm.SECRET;
    protected String secret;
    protected String pemPath;
    protected boolean enabled = false;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     *
     * @return The algorithm to be used to sign the token
     */
    @Override
    public CryptoAlgorithm getType() {
        return type;
    }

    @Override
    public JWSAlgorithm getJwsAlgorithm() {
        return jwsAlgorithm;
    }

    /**
     *
     * @return Secret's length must be at least 256 bits. it is used to sign JWT.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @return The path to the PEM file
     */
    public String getPemPath() {
        return pemPath;
    }
}
