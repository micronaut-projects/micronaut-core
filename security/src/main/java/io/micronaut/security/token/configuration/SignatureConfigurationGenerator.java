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

import io.micronaut.context.annotation.Requires;
import org.pac4j.jwt.config.signature.ECSignatureConfiguration;
import org.pac4j.jwt.config.signature.RSASignatureConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;

import javax.inject.Singleton;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Generates a Pac4j SignatureConfiguration.
 * @see <a href="https://www.pac4j.org/docs/authenticators/jwt.html">Pac4j JWT authenticators</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = TokenConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Singleton
public class SignatureConfigurationGenerator {
    Optional<SignatureConfiguration> signatureConfiguration;

    /**
     *
     * @param tokenSignatureConfiguration Instance of {@link TokenSignatureConfiguration}
     */
    public SignatureConfigurationGenerator(TokenSignatureConfiguration tokenSignatureConfiguration) {
        this.signatureConfiguration = createSignatureConfiguration(tokenSignatureConfiguration);
    }

    /**
     *
     * @return the Pac4j Signature Configuration
     */
    public Optional<SignatureConfiguration> getSignatureConfiguration() {
        return signatureConfiguration;
    }

    /**
     *
     * @param tokenSignatureConfiguration Instance of {@link TokenSignatureConfiguration}
     * @return SignatureConfiguration if enabled
     */
    private Optional<SignatureConfiguration> createSignatureConfiguration(TokenSignatureConfiguration tokenSignatureConfiguration) {
        if (!tokenSignatureConfiguration.isEnabled()) {
            return Optional.empty();
        }
        try {
            switch (tokenSignatureConfiguration.getType()) {
                case SECRET:
                    return Optional.of(new SecretSignatureConfiguration(tokenSignatureConfiguration.getSecret(), tokenSignatureConfiguration.getJwsAlgorithm()));
                case RSA:
                    KeyPairGenerator rsakeyGen = KeyPairGenerator.getInstance("RSA");
                    KeyPair rsaKeyPair = rsakeyGen.generateKeyPair();
                    return Optional.of(new RSASignatureConfiguration(rsaKeyPair, tokenSignatureConfiguration.getJwsAlgorithm()));
                case EC:
                    KeyPairGenerator ecKeyGen = KeyPairGenerator.getInstance("EC");
                    KeyPair ecKeyPair = ecKeyGen.generateKeyPair();
                    return Optional.of(new ECSignatureConfiguration(ecKeyPair, tokenSignatureConfiguration.getJwsAlgorithm()));
                default:
                    return Optional.empty();
            }
        } catch (NoSuchAlgorithmException e) {

        }
        return Optional.empty();
    }
}
