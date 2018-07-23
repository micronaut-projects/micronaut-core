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

import javax.inject.Singleton;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

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

    /**
     *
     * @param signatureConfigurations List of Signature configurations which are used to attempt validation.
     * @param encryptionConfigurations List of Encryption configurations which are used to attempt validation.
     */
    public JwtTokenValidator(Collection<SignatureConfiguration> signatureConfigurations,
                             Collection<EncryptionConfiguration> encryptionConfigurations) {
        this.signatureConfigurations.addAll(signatureConfigurations);
        this.encryptionConfigurations.addAll(encryptionConfigurations);
    }

    private boolean validateExpirationTime(JWTClaimsSet claimSet) {
        final Date expTime = claimSet.getExpirationTime();
        if (expTime != null) {
            final Date now = new Date();
            if (expTime.before(now)) {
                return false;
            }
        }
        return true;
    }

    private Publisher<Authentication> validatePlainJWT(JWT jwt) throws ParseException {
        if (signatureConfigurations.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("JWT is not signed and no signature configurations -> verified");
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("A non-signed JWT cannot be accepted as signature configurations have been defined");
            }
            return Flowable.empty();
        }
        return createAuthentication(jwt);
    }

    private Publisher<Authentication> validateSignedJWT(SignedJWT signedJWT) throws ParseException {
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
                        return createAuthentication(signedJWT);
                    } else {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("JWT verification failed: {}", signedJWT.getParsedString());
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
        return Flowable.empty();
    }

    private Publisher<Authentication> validateEncryptedJWT(JWT jwt, EncryptedJWT encryptedJWT, String token) throws ParseException  {
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
                        return Flowable.empty();
                    }
                    return validateSignedJWT(signedJWT);

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
        return Flowable.empty();
    }

    @Override
    public Publisher<Authentication> validateToken(String token) {
        try {
            // Parse the token
            JWT jwt = JWTParser.parse(token);

            if (jwt instanceof PlainJWT) {
                return validatePlainJWT(jwt);

            } else if (jwt instanceof EncryptedJWT) {
                final EncryptedJWT encryptedJWT = (EncryptedJWT) jwt;
                return validateEncryptedJWT(jwt, encryptedJWT, token);

            } else if (jwt instanceof SignedJWT) {
                final SignedJWT signedJWT = (SignedJWT) jwt;
                return validateSignedJWT(signedJWT);
            }

            return Flowable.empty();

        } catch (final ParseException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot decrypt / verify JWT: {}", e.getMessage());
            }
            return Flowable.empty();
        }
    }

    private Publisher<Authentication> createAuthentication(final JWT jwt) throws ParseException {
        final JWTClaimsSet claimSet = jwt.getJWTClaimsSet();
        final String subject = claimSet.getSubject();
        if (subject == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("JWT must contain a subject ('sub' claim)");
            }
            return Flowable.empty();
        }
        if (!validateExpirationTime(jwt.getJWTClaimsSet())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("JWT expired");
            }
            return Flowable.empty();
        }
        return Flowable.just(new AuthenticationJWTClaimsSetAdapter(claimSet));
    }
}
