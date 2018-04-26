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

package io.micronaut.security.jwt.validator;

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
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.jwt.config.JwtConfigurationProperties;
import io.micronaut.security.jwt.encryption.EncryptionConfiguration;
import io.micronaut.security.jwt.encryption.JwtEncryptionConfiguration;
import io.micronaut.security.jwt.encryption.JwtEncryptionConfigurationFactory;
import io.micronaut.security.jwt.signature.JwtSignatureConfiguration;
import io.micronaut.security.jwt.signature.JwtSignatureConfigurationFactory;
import io.micronaut.security.jwt.signature.SignatureConfiguration;
import io.micronaut.security.token.validator.TokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @see <a href="https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens">Validating JWT Access Tokens</a>
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = JwtConfigurationProperties.PREFIX + ".enabled")
@Singleton
public class JwtTokenValidator implements TokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenValidator.class);

    protected final List<SignatureConfiguration> signatureConfigurations = new ArrayList<>();
    protected final List<EncryptionConfiguration> encryptionConfigurations = new ArrayList<>();

    /**
     *
     * @param jwtSignatureConfigurations List of Signature configurations which are used to attempt validation.
     * @param jwtEncryptionConfigurations List of Encryption configurations which are used to attempt validation.
     */
    public JwtTokenValidator(Collection<JwtSignatureConfiguration> jwtSignatureConfigurations,
                             Collection<JwtEncryptionConfiguration> jwtEncryptionConfigurations) {

        this.signatureConfigurations.addAll(jwtSignatureConfigurations
                .stream()
                .filter(JwtSignatureConfiguration::isEnabled)
                .map(JwtSignatureConfigurationFactory::create)
                .collect(Collectors.toList()));

        this.encryptionConfigurations.addAll(jwtEncryptionConfigurations
                .stream()
                .filter(JwtEncryptionConfiguration::isEnabled)
                .map(JwtEncryptionConfigurationFactory::create)
                .collect(Collectors.toList()));
    }

    @Override
    public Optional<Authentication> validateToken(String token) {
        try {
            // Parse the token
            JWT jwt = JWTParser.parse(token);

            if (jwt instanceof PlainJWT) {
                if (signatureConfigurations.isEmpty()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("JWT is not signed and no signature configurations -> verified");
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("A non-signed JWT cannot be accepted as signature configurations have been defined");
                    }
                    return Optional.empty();
                }
            } else {

                SignedJWT signedJWT = null;
                if (jwt instanceof SignedJWT) {
                    signedJWT = (SignedJWT) jwt;
                }

                // encrypted?
                if (jwt instanceof EncryptedJWT) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("JWT is encrypted");
                    }

                    final EncryptedJWT encryptedJWT = (EncryptedJWT) jwt;
                    boolean found = false;
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
                                signedJWT = encryptedJWT.getPayload().toSignedJWT();
                                if (signedJWT != null) {
                                    jwt = signedJWT;
                                }
                                found = true;
                                break;
                            } catch (final JOSEException e) {
                                if (LOG.isDebugEnabled()) {
                                    LOG.debug("Decryption fails with encryption configuration: {}, passing to the next one", config.toString());
                                }
                            }
                        }
                    }
                    if (!found) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("No encryption algorithm found for JWT: {}", token);
                        }
                        return Optional.empty();
                    }
                }

                // signed?
                if (signedJWT != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("JWT is signed");
                    }

                    boolean verified = false;
                    boolean found = false;
                    final JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
                    for (final SignatureConfiguration config : signatureConfigurations) {
                        if (config.supports(algorithm)) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Using signature configuration: {}", config.toString());
                            }
                            try {
                                verified = config.verify(signedJWT);
                                found = true;
                                if (verified) {
                                    break;
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
                    if (!found) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("No signature algorithm found for JWT: {}", token);
                        }
                        return Optional.empty();
                    }
                    if (!verified) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("JWT verification failed: {}", token);
                        }
                        return Optional.empty();
                    }
                }
            }

            return createAuthentication(jwt);

        } catch (final ParseException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Cannot decrypt / verify JWT: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    private Optional<Authentication> createAuthentication(final JWT jwt) throws ParseException {
        final JWTClaimsSet claimSet = jwt.getJWTClaimsSet();
        final String subject = claimSet.getSubject();
        if (subject == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("JWT must contain a subject ('sub' claim)");
            }
            return Optional.empty();
        }

        return Optional.of(new AuthenticationJWTClaimsSetAdapter(claimSet));
    }
}
