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

package io.micronaut.security.token.validator;

import io.micronaut.security.token.configuration.EncryptionConfigurationGenerator;
import io.micronaut.security.token.configuration.SignatureConfigurationGenerator;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Token Validator based on Pac4j.
 * @see <a href="https://www.pac4j.org/docs/authenticators/jwt.html">Pac4j JWT authenticators</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JwtTokenValidator implements TokenValidator {

    private JwtAuthenticator jwtAuthenticator;

    /**
     *
     * @param signatureConfigurationGenerator Utility to retrieve a Pac4j SignatureConfiguration
     * @param encryptionConfigurationGenerator Utility to retrieve a Pac4j EncryptionConfiguration
     */
    public JwtTokenValidator(SignatureConfigurationGenerator signatureConfigurationGenerator,
                       EncryptionConfigurationGenerator encryptionConfigurationGenerator) {

        jwtAuthenticator = new JwtAuthenticator();
        Optional<SignatureConfiguration> signatureConfiguration = signatureConfigurationGenerator.getSignatureConfiguration();
        signatureConfiguration.ifPresent(signatureConfiguration1 -> jwtAuthenticator.addSignatureConfiguration(signatureConfiguration1));
        Optional<EncryptionConfiguration> encryptionConfiguration = encryptionConfigurationGenerator.getEncryptionConfiguration();
        encryptionConfiguration.ifPresent(encryptionConfiguration1 -> jwtAuthenticator.addEncryptionConfiguration(encryptionConfiguration1));
    }

    @Override
    public Optional<Map<String, Object>> validateTokenAndGetClaims(String token) {
        try {
            CommonProfile commonProfile = jwtAuthenticator.validateToken(token);
            if (commonProfile != null) {
                Map<String, Object> claims = jwtAuthenticator.validateTokenAndGetClaims(token);
                return Optional.of(claims);
            }
            return Optional.empty();

        } catch ( TechnicalException e ) {
            return Optional.empty();
        }
    }
}
