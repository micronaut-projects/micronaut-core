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

package io.micronaut.security.token.jwt.generator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.generator.TokenGenerator;
import io.micronaut.security.token.jwt.generator.claims.ClaimsGenerator;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Token Generation.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JwtTokenGenerator implements TokenGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenValidator.class);

    protected final ClaimsGenerator claimsGenerator;
    protected final SignatureConfiguration signatureConfiguration;
    protected final EncryptionConfiguration encryptionConfiguration;

    /**
     * @param signatureConfiguration JWT Generator signature configuration
     * @param encryptionConfiguration JWT Generator encryption configuration
     * @param claimsGenerator Claims generator
     */
    public JwtTokenGenerator(@Nullable @Named("generator") SignatureConfiguration signatureConfiguration,
                             @Nullable @Named("generator") EncryptionConfiguration encryptionConfiguration,
                             ClaimsGenerator claimsGenerator) {

        this.signatureConfiguration = signatureConfiguration;
        this.encryptionConfiguration = encryptionConfiguration;
        this.claimsGenerator = claimsGenerator;
    }

    /**
     * signatureConfiguration getter.
     * @return Instance of {@link SignatureConfiguration}
     */
    public SignatureConfiguration getSignatureConfiguration() {
        return this.signatureConfiguration;
    }

    /**
     * encryptionConfiguration getter.
     * @return Instance of {@link EncryptionConfiguration}
     */
    public EncryptionConfiguration getEncryptionConfiguration() {
        return this.encryptionConfiguration;
    }

    /**
     * Generate a JWT from a claims set.
     * @throws JOSEException thrown in the JWT generation
     * @throws ParseException thrown in the JWT generation
     * @param claimsSet the claims set
     * @return the JWT
     */
    protected String internalGenerate(final JWTClaimsSet claimsSet) throws JOSEException, ParseException {
        JWT jwt;
        // signature?
        if (signatureConfiguration == null) {
            jwt = new PlainJWT(claimsSet);
        } else {
            jwt = signatureConfiguration.sign(claimsSet);
        }

        // encryption?
        if (encryptionConfiguration != null) {
            return encryptionConfiguration.encrypt(jwt);
        } else {
            return jwt.serialize();
        }
    }

    /**
     * Generate a JWT from a map of claims.
     * @throws JOSEException thrown in the JWT generation
     * @throws ParseException thrown in the JWT generation
     * @param claims the map of claims
     * @return the created JWT
     */
    protected String generate(final Map<String, Object> claims) throws JOSEException, ParseException {
        // claims builder
        final JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();

        // add claims
        for (final Map.Entry<String, Object> entry : claims.entrySet()) {
            builder.claim(entry.getKey(), entry.getValue());
        }

        return internalGenerate(builder.build());
    }

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param expiration The amount of time in milliseconds until the token expires
     * @return JWT token
     */
    @Override
    public Optional<String> generateToken(UserDetails userDetails, @Nullable Integer expiration) {
        Map<String, Object> claims = claimsGenerator.generateClaims(userDetails, expiration);
        return generateToken(claims);
    }

    /**
     *
     * @param claims JWT claims
     * @return JWT token
     */
    @Override
    public Optional<String> generateToken(Map<String, Object> claims) {
        try {
            return Optional.of(generate(claims));
        } catch (ParseException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Parse exception while generating token {}", e.getMessage());
            }
        } catch (JOSEException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("JOSEException while generating token {}", e.getMessage());
            }
        }
        return Optional.empty();
    }
}
