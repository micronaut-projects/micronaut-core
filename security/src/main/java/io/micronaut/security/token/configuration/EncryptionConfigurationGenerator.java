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

import io.micronaut.security.token.generator.EncryptionKeyProvider;
import org.pac4j.jwt.config.encryption.ECEncryptionConfiguration;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.encryption.RSAEncryptionConfiguration;
import org.pac4j.jwt.config.encryption.SecretEncryptionConfiguration;

import javax.inject.Singleton;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Generates a Pac4j EncryptionConfiguration.
 * @see <a href="https://www.pac4j.org/docs/authenticators/jwt.html">Pac4j JWT authenticators</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class EncryptionConfigurationGenerator {

    private Optional<EncryptionConfiguration> encryptionConfiguration;

    /**
     *
     * @param tokenEncryptionConfiguration Instance of {@link TokenEncryptionConfiguration}
     * @param encryptionKeyProvider Instance of {@link EncryptionKeyProvider}
     */
    public EncryptionConfigurationGenerator(TokenEncryptionConfiguration tokenEncryptionConfiguration,
                                            EncryptionKeyProvider encryptionKeyProvider) {
        this.encryptionConfiguration = createEncryptionConfiguration(tokenEncryptionConfiguration,
                encryptionKeyProvider);
    }

    /**
     *
     * @return  a Pacj4 encryption configuration
     */
    public Optional<EncryptionConfiguration> getEncryptionConfiguration() {
        return this.encryptionConfiguration;
    }

    /**
     *
     * @param tokenEncryptionConfiguration Instance of {@link TokenEncryptionConfiguration}
     * @return EncryptionConfiguration if enabled
     */
    private static Optional<EncryptionConfiguration> createEncryptionConfiguration(TokenEncryptionConfiguration tokenEncryptionConfiguration,
                                                                                  EncryptionKeyProvider encryptionKeyProvider) {
        if ( !tokenEncryptionConfiguration.isEnabled() ) {
            return Optional.empty();
        }
        try {
            switch (tokenEncryptionConfiguration.getType()) {
                case SECRET:
                    SecretEncryptionConfiguration secretEncryptionConfiguration = new SecretEncryptionConfiguration(tokenEncryptionConfiguration.getSecret(),
                            tokenEncryptionConfiguration.getJweAlgorithm(),
                            tokenEncryptionConfiguration.getEncryptionMethod());
                    return Optional.of(secretEncryptionConfiguration);

                case RSA:
                    KeyPair rsaKeyPair = new KeyPair(encryptionKeyProvider.getPublicKey(), encryptionKeyProvider.getPrivateKey());
                    RSAEncryptionConfiguration rsaEncConfig = new RSAEncryptionConfiguration(rsaKeyPair);
                    rsaEncConfig.setAlgorithm(tokenEncryptionConfiguration.getJweAlgorithm());
                    rsaEncConfig.setMethod(tokenEncryptionConfiguration.getEncryptionMethod());
                    return Optional.of(rsaEncConfig);

                case EC:
                    KeyPairGenerator ecKeyGen = KeyPairGenerator.getInstance("EC");
                    KeyPair ecKeyPair = ecKeyGen.generateKeyPair();
                    ECEncryptionConfiguration encConfig = new ECEncryptionConfiguration(ecKeyPair);
                    encConfig.setAlgorithm(tokenEncryptionConfiguration.getJweAlgorithm());
                    encConfig.setMethod(tokenEncryptionConfiguration.getEncryptionMethod());
                    return Optional.of(encConfig);
                default:
                    return Optional.empty();
            }
        } catch (NoSuchAlgorithmException e) {

        }
        return Optional.empty();
    }
}
