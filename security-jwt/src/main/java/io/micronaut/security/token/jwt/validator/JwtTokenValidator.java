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

package io.micronaut.security.token.jwt.validator;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import io.micronaut.security.token.validator.TokenValidator;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @see <a href="https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens">Validating JWT Access Tokens</a>
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JwtTokenValidator implements TokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenValidator.class);

    protected final List<SignatureConfiguration> signatureConfigurations = new ArrayList<>();
    protected final List<EncryptionConfiguration> encryptionConfigurations = new ArrayList<>();
    protected final List<GenericJwtClaimsValidator> genericJwtClaimsValidators = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param signatureConfigurations List of Signature configurations which are used to attempt validation.
     * @param encryptionConfigurations List of Encryption configurations which are used to attempt validation.
     * @param genericJwtClaimsValidators Generic JWT Claims validators which should be used to validate any JWT.
     */
    @Inject
    public JwtTokenValidator(Collection<SignatureConfiguration> signatureConfigurations,
                             Collection<EncryptionConfiguration> encryptionConfigurations,
                             Collection<GenericJwtClaimsValidator> genericJwtClaimsValidators) {
        this.signatureConfigurations.addAll(signatureConfigurations);
        this.encryptionConfigurations.addAll(encryptionConfigurations);
        this.genericJwtClaimsValidators.addAll(genericJwtClaimsValidators);
    }

    /**
     *
     * Deprecated Constructor.
     *
     * @deprecated Use {@link JwtTokenValidator#JwtTokenValidator(Collection, Collection, Collection)} instead.
     * @param signatureConfigurations List of Signature configurations which are used to attempt validation.
     * @param encryptionConfigurations List of Encryption configurations which are used to attempt validation.
     */
    @Deprecated
    public JwtTokenValidator(Collection<SignatureConfiguration> signatureConfigurations,
                             Collection<EncryptionConfiguration> encryptionConfigurations) {
        this(signatureConfigurations,
                encryptionConfigurations,
                Collections.singleton(new ExpirationJwtClaimsValidator()));
    }



    /**
     * Validates the Signature of a plain JWT.
     * @param jwt a JWT Token
     * @return empty if signature configurations exists, Optional.of(jwt) if no signature configuration is available.
     */
    protected Optional<JWT> validatePlainJWTSignature(JWT jwt) {
        if (signatureConfigurations.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("JWT is not signed and no signature configurations -> verified");
            }
            return Optional.of(jwt);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("A non-signed JWT cannot be accepted as signature configurations have been defined");
            }
            return Optional.empty();
        }
    }

    /**
     *
     * Validates a Signed JWT signature.
     *
     * @param signedJWT a Signed JWT Token
     * @return empty if signature validation fails
     */
    protected  Optional<JWT> validateSignedJWTSignature(SignedJWT signedJWT) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("JWT is signed");
        }

        final JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
        for (final SignatureConfiguration config : signatureConfigurations) {
            if (config.supports(algorithm)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using signature configuration: {}", config.toString());
                }
                try {
                    if (config.verify(signedJWT)) {
                        return Optional.of(signedJWT);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("JWT Signature verification failed: {}", signedJWT.getParsedString());
                        }
                    }
                } catch (final JOSEException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Verification fails with signature configuration: {}, passing to the next one", config);
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}", config.supportedAlgorithmsMessage());
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No signature algorithm found for JWT: {}", signedJWT.getParsedString());
        }
        return Optional.empty();
    }

    /**
     * Verifies the provided claims with the provided validators.
     *
     * @param jwtClaimsSet JWT Claims
     * @param claimsValidators The claims validators
     * @return Whether the JWT claims pass every validation.
     */
    protected boolean verifyClaims(JWTClaimsSet jwtClaimsSet, Collection<? extends JwtClaimsValidator> claimsValidators) {
        return claimsValidators.stream()
                .allMatch(jwtClaimsValidator -> jwtClaimsValidator.validate(jwtClaimsSet));
    }

    /**
     *
     * Validates a encrypted JWT Signature.
     *
     * @param encryptedJWT a encrytped JWT Token
     * @param token the JWT token as String
     * @return empty if signature validation fails
     */
    protected Optional<JWT> validateEncryptedJWTSignature(EncryptedJWT encryptedJWT, String token) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("JWT is encrypted");
        }

        final JWEHeader header = encryptedJWT.getHeader();
        final JWEAlgorithm algorithm = header.getAlgorithm();
        final EncryptionMethod method = header.getEncryptionMethod();
        for (final EncryptionConfiguration config : encryptionConfigurations) {
            if (config.supports(algorithm, method)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using encryption configuration: {}", config.toString());
                }
                try {
                    config.decrypt(encryptedJWT);
                    SignedJWT signedJWT = encryptedJWT.getPayload().toSignedJWT();
                    if (signedJWT == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("encrypted JWT could couldn't be converted to a signed JWT.");
                        }
                        return Optional.empty();
                    }
                    return validateSignedJWTSignature(signedJWT);

                } catch (final JOSEException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Decryption fails with encryption configuration: {}, passing to the next one", config.toString());
                    }
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No encryption algorithm found for JWT: {}", token);
        }
        return Optional.empty();
    }

    /**
     *
     * @param token The token string.
     * @return Publishes {@link Authentication} based on the JWT or empty if the validation fails.
     */
    @Override
    public Publisher<Authentication> validateToken(String token) {
        Optional<Authentication> authentication = authenticationIfValidJwtSignatureAndClaims(token, genericJwtClaimsValidators);
        if (authentication.isPresent()) {
            return Flowable.just(authentication.get());
        }
        return Flowable.empty();
    }

    /**
     * Authentication if JWT has valid signature and claims are verified.
     *
     * @param token A JWT token
     * @param claimsValidators a Collection of claims Validators.
     * @return empty if signature or claims verification failed, An Authentication otherwise.
     */
    public Optional<Authentication> authenticationIfValidJwtSignatureAndClaims(String token, Collection<? extends JwtClaimsValidator> claimsValidators) {
        Optional<JWT> jwt = validateJwtSignatureAndClaims(token, claimsValidators);
        if (jwt.isPresent()) {
            try {
                return Optional.of(createAuthentication(jwt.get()));
            } catch (ParseException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("ParseException creating authentication", e.getMessage());
                }
            }
        }
        return Optional.empty();

    }

    /**
     * Validates JWT signature and Claims.
     *
     * @param token A JWT token
     * @param claimsValidators a Collection of claims Validators.
     * @return empty if signature or claims verification failed, JWT otherwise.
     */
    public Optional<JWT> validateJwtSignatureAndClaims(String token, Collection<? extends JwtClaimsValidator> claimsValidators) {
        Optional<JWT> jwt = parseJwtIfValidSignature(token);
        if (jwt.isPresent()) {
            try {
                if (verifyClaims(jwt.get().getJWTClaimsSet(), claimsValidators)) {
                    return jwt;
                }
            } catch (ParseException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("ParseException creating authentication", e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retuns a JWT if the signature could be verified.
     * @param token a JWT token
     * @return Empty if JWT signature verification failed or JWT if valid signature.
     */
    protected Optional<JWT> parseJwtIfValidSignature(String token) {
        try {
            JWT jwt = JWTParser.parse(token);

            if (jwt instanceof PlainJWT) {
                return validatePlainJWTSignature(jwt);

            } else if (jwt instanceof EncryptedJWT) {
                final EncryptedJWT encryptedJWT = (EncryptedJWT) jwt;
                return validateEncryptedJWTSignature(encryptedJWT, token);

            } else if (jwt instanceof SignedJWT) {
                final SignedJWT signedJWT = (SignedJWT) jwt;
                return validateSignedJWTSignature(signedJWT);
            }

        } catch (final ParseException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot decrypt / verify JWT: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     *
     * @param jwt a JWT token
     * @return {@link Authentication} based on the JWT.
     * @throws ParseException it may throw a ParseException while retrieving the JWT claims
     */
    protected Authentication createAuthentication(final JWT jwt) throws ParseException {
        final JWTClaimsSet claimSet = jwt.getJWTClaimsSet();
        return new AuthenticationJWTClaimsSetAdapter(claimSet);
    }
}
