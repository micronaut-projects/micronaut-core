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

import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.configuration.EncryptionConfigurationGenerator;
import io.micronaut.security.token.configuration.SignatureConfigurationGenerator;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.signature.SignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Token Generation based on Pac4j.
 * @see <a href="https://www.pac4j.org/docs/authenticators/jwt.html">Pac4j JWT authenticators</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JwtTokenGenerator implements TokenGenerator {

    protected final JWTClaimsSetGenerator claimsGenerator;
    private JwtGenerator<CommonProfile> jwtGenerator;

    /**
     *
     * @param signatureConfigurationGenerator Utility to retrieve a Pac4j SignatureConfiguration
     * @param encryptionConfigurationGenerator Utility to retrieve a Pac4j EncryptionConfiguration
     * @param claimsGenerator Claims generator
     */
    public JwtTokenGenerator(SignatureConfigurationGenerator signatureConfigurationGenerator,
                             EncryptionConfigurationGenerator encryptionConfigurationGenerator,
                             JWTClaimsSetGenerator claimsGenerator) {
        this.claimsGenerator = claimsGenerator;

        Optional<SignatureConfiguration> signatureConfiguration = signatureConfigurationGenerator.getSignatureConfiguration();
        Optional<EncryptionConfiguration> encryptionConfiguration = encryptionConfigurationGenerator.getEncryptionConfiguration();

        if (signatureConfiguration.isPresent() && encryptionConfiguration.isPresent()) {
            this.jwtGenerator = new JwtGenerator<>(signatureConfiguration.get(), encryptionConfiguration.get());
        } else {
            this.jwtGenerator = signatureConfiguration.map(JwtGenerator::new).orElseGet(JwtGenerator::new);
        }
    }

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param expiration The amount of time in milliseconds until the token expires
     * @return JWT token
     */
    @Override
    public String generateToken(UserDetails userDetails, @Nullable Integer expiration) {
        Map<String, Object> claims = claimsGenerator.generateClaims(userDetails, expiration);
        return generateToken(claims);
    }

    /**
     *
     * @param claims JWT claims
     * @return JWT token
     */
    @Override
    public String generateToken(Map<String, Object> claims) {
        return jwtGenerator.generate(claims);
    }
}
