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

package io.micronaut.security.token.jwt.encryption;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
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
@ConfigurationProperties(JwtGeneratorEncryptionConfigurationProperties.PREFIX)
public class JwtGeneratorEncryptionConfigurationProperties implements JwtGeneratorEncryptionConfiguration {
    public static final String PREFIX = JwtGeneratorConfigurationProperties.PREFIX + ".encryption";

    protected EncryptionMethod encryptionMethod = EncryptionMethod.A128GCM;
    protected JWEAlgorithm jweAlgorithm = JWEAlgorithm.RSA_OAEP_256;
    protected CryptoAlgorithm type = CryptoAlgorithm.RSA;
    protected String secret;
    protected String pemPath;
    protected boolean enabled = false;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The algorithm to encrypt the token
     */
    public CryptoAlgorithm getType() {
        return this.type;
    }

    /**
     * @return The path to the PEM file
     */
    public String getPemPath() {
        return pemPath;
    }

    /**
     * @return The JWE algorithm
     */
    public JWEAlgorithm getJweAlgorithm() {
        return this.jweAlgorithm;
    }

    /**
     * encryptionMethod getter.
     * @return Instance of {@link EncryptionMethod}
     */
    public EncryptionMethod getEncryptionMethod() {
        return encryptionMethod;
    }

    /**
     * secret Getter.
     * @return secret string.
     */
    public String getSecret() {
        return this.secret;
    }
}
