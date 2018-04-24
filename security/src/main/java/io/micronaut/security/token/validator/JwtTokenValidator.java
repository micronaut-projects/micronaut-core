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

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.configuration.EncryptionConfigurationGenerator;
import io.micronaut.security.token.configuration.SignatureConfigurationGenerator;
import io.micronaut.security.token.configuration.TokenConfigurationProperties;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.jwt.JwtClaims;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Token Validator based on Pac4j.
 * @see <a href="https://www.pac4j.org/docs/authenticators/jwt.html">Pac4j JWT authenticators</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = TokenConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Singleton
public class JwtTokenValidator implements TokenValidator {

    /**
     * The order of the TokenValidator.
     */
    public static final Integer ORDER = BasicAuthTokenValidator.ORDER - 100;

    private JwtAuthenticator jwtAuthenticator;

    /**
     *
     * @param signatureConfigurationGenerator Utility to retrieve a Pac4j SignatureConfiguration
     * @param encryptionConfigurationGenerator Utility to retrieve a Pac4j EncryptionConfiguration
     */
    public JwtTokenValidator(SignatureConfigurationGenerator signatureConfigurationGenerator,
                             EncryptionConfigurationGenerator encryptionConfigurationGenerator) {

        jwtAuthenticator = new JwtAuthenticator();
        signatureConfigurationGenerator.getSignatureConfiguration().ifPresent(signatureConfiguration ->
                jwtAuthenticator.addSignatureConfiguration(signatureConfiguration));
        encryptionConfigurationGenerator.getEncryptionConfiguration().ifPresent(encryptionConfiguration ->
                jwtAuthenticator.addEncryptionConfiguration(encryptionConfiguration));
    }

    @Override
    public Optional<Map<String, Object>> validateTokenAndGetClaims(String token) {
        return validateToken(token).map(Authentication::getAttributes);
    }

    @Override
    public Optional<Authentication> validateToken(String token) {
        CommonProfile profile;
        try {
            profile = jwtAuthenticator.validateToken(token);
        } catch (TechnicalException e) {
            return Optional.empty();
        }

        if (profile != null) {
            return Optional.of(new Authentication() {
                @Override
                public String getId() {
                    return profile.getId();
                }

                @Override
                public Map<String, Object> getAttributes() {
                    final Map<String, Object> claims = new HashMap<>(profile.getAttributes());
                    claims.put(JwtClaims.SUBJECT, profile.getId());
                    return claims;
                }
            });
        }
        return Optional.empty();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
