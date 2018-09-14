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

package io.micronaut.security.token.jwt.encryption.secret;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

/**
 * Encapsulates Secret Encryption Configuration.
 * @author Sergio del Amo
 * @since 1.0
 */
@EachProperty(JwtConfigurationProperties.PREFIX + ".encryptions.secret")
public class SecretEncryptionConfiguration {
    private JWEAlgorithm jweAlgorithm;
    private EncryptionMethod encryptionMethod;
    private String secret;
    private final String name;

    /**
     * Constructor.
     * @param name Bean name qualifier.
     */
    public SecretEncryptionConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     *
     * @return The secret being used in {@link SecretEncryption}
     */
    public String getSecret() {
        return secret;
    }

    /**
     * @return The JWE algorithm
     */
    public JWEAlgorithm getJweAlgorithm() {
        return jweAlgorithm;
    }

    /**
     * {@link com.nimbusds.jose.JWEAlgorithm}.
     *
     * @param jweAlgorithm JWT Algorithm
     */
    public void setJweAlgorithm(JWEAlgorithm jweAlgorithm) {
        this.jweAlgorithm = jweAlgorithm;
    }

    /**
     * {@link com.nimbusds.jose.EncryptionMethod}.
     *
     * @param encryptionMethod Encryption Method
     */
    public void setEncryptionMethod(EncryptionMethod encryptionMethod) {
        this.encryptionMethod = encryptionMethod;
    }

    /**
     *
     * @return {@link EncryptionMethod}
     */
    public EncryptionMethod getEncryptionMethod() {
        return encryptionMethod;
    }

    /**
     * Secret used for encryption configuration.
     * @param secret Encryption secret.
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     *
     * @return Bean qualifier name.
     */
    public String getName() {
        return name;
    }
}
