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

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.generator.TokenConfiguration;
import io.micronaut.security.token.generator.TokenEncryptionConfigurationProperties;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.jwt.JwtClaims;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(property = TokenEncryptionConfigurationProperties.PREFIX + ".enabled", notEquals = "true")
public class SignedJwtTokenValidator implements TokenValidator {

    private final JwtAuthenticator jwtAuthenticator;

    /**
     *
     * @param tokenConfiguration ConfigurationProperties file for token settings
     */
    public SignedJwtTokenValidator(TokenConfiguration tokenConfiguration) {
        final JWSAlgorithm jwsAlgorithm = tokenConfiguration.getJwsAlgorithm();
        final String secret = tokenConfiguration.getSecret();
        final SecretSignatureConfiguration signatureConfiguration = new SecretSignatureConfiguration(secret, jwsAlgorithm);
        jwtAuthenticator = new JwtAuthenticator();
        jwtAuthenticator.addSignatureConfiguration(signatureConfiguration);
    }

    @Override
    public Optional<Map<String, Object>> validateTokenAndGetClaims(String token) {
        CommonProfile profile = jwtAuthenticator.validateToken(token);
        if ( profile != null && profile.getAttributes() != null) {
            return Optional.of(claimsOfProfile(profile));
        }
        return Optional.empty();
    }

    /**
     *
     * @param profile a Pac4j CommonProfile
     * @return a Map containing the JWT Claims
     */
    protected Map<String, Object> claimsOfProfile(CommonProfile profile) {
        Map<String, Object> claims = new HashMap<>();
        Map<String, Object> attributes = profile.getAttributes();
        if (attributes != null) {
            claims.putAll(new HashMap<>(attributes));
        }
        claims.put(JwtClaims.SUBJECT, profile.getId());
        return claims;
    }
}
