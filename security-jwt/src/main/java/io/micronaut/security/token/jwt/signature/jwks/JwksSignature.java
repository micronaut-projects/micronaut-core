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

package io.micronaut.security.token.jwt.signature.jwks;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.core.util.functional.ThrowingSupplier;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Signature configuration which enables verification of remote JSON Web Key Set.
 *
 * A bean of this class is created for each {@link io.micronaut.security.token.jwt.signature.jwks.JwksSignatureConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@EachBean(JwksSignatureConfiguration.class)
public class JwksSignature implements SignatureConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JwksSignature.class);
    private static final int REFRESH_JWKS_ATTEMPTS = 1;

    @Nullable
    private JWKSet jwkSet;

    @NotNull
    private final KeyType keyType;

    @NotNull
    private final String url;

    /**
     *
     * @param jwksSignatureConfiguration JSON Web Key Set configuration.
     */
    public JwksSignature(JwksSignatureConfiguration jwksSignatureConfiguration) {
        this.url = jwksSignatureConfiguration.getUrl();
        if (LOG.isDebugEnabled()) {
            LOG.debug("JWT validation URL: {}", url);
        }
        this.jwkSet = loadJwkSet(url);
        this.keyType = jwksSignatureConfiguration.getKeyType();
    }


    private Optional<JWKSet> getJWKSet() {
        return Optional.ofNullable(jwkSet);
    }

    private List<JWK> getJsonWebKeys() {
        return getJWKSet().map(JWKSet::getKeys).orElse(Collections.emptyList());
    }

    /**
     *
     * @return A message indicating the supported algorithms.
     */
    @Override
    public String supportedAlgorithmsMessage() {
        String message = getJsonWebKeys().stream()
                .map(JWK::getAlgorithm)
                .map(Algorithm::getName)
                .reduce((a, b) -> a + ", " + b)
                .map(s -> "Only the " + s)
                .orElse("No");
        return message + " algorithms are supported";
    }

    /**
     * Whether this signature configuration supports this algorithm.
     *
     * @param algorithm the signature algorithm
     * @return whether this signature configuration supports this algorithm
     */
    @Override
    public boolean supports(JWSAlgorithm algorithm) {
        return getJsonWebKeys()
                .stream()
                .map(JWK::getAlgorithm)
                .anyMatch(algorithm::equals);
    }

    /**
     * Verify a signed JWT.
     *
     * @param jwt the signed JWT
     * @return whether the signed JWT is verified
     * @throws JOSEException exception when verifying the JWT
     */
    @Override
    public boolean verify(SignedJWT jwt) throws JOSEException {
        List<JWK> matches = matches(jwt, getJWKSet().orElse(null), REFRESH_JWKS_ATTEMPTS);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Found {} matching JWKs", matches.size());
        }
        if (matches == null || matches.isEmpty()) {
            return false;
        }
        return verify(matches, jwt);
    }

    /**
     * Instantiates a JWKSet for a give url.
     * @param url JSON Web Key Set Url.
     * @return a JWKSet or null if there was an error.
     */
    protected JWKSet loadJwkSet(String url) {
        try {
            return JWKSet.load(new URL(url));
        } catch (IOException | ParseException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Exception loading JWK. The JwksSignature will not be used to verify a JWT if further refresh attempts fail", e);
            }
        }

        return null;
    }

    /**
     * Calculates a list of JWK matches for a JWT.
     *
     * Since the JWTSet is cached it attempts to refresh it (by calling its self recursive)
     * if the {@param refreshKeysAttempts} is > 0.
     *
     * @param jwt A Signed JWT
     * @param jwkSet A JSON Web Key Set
     * @param refreshKeysAttempts Number of times to attempt refreshing the JWK Set
     * @return a List of JSON Web Keys
     */
    protected List<JWK> matches(SignedJWT jwt, @Nullable JWKSet jwkSet, int refreshKeysAttempts) {

        String keyId = jwt.getHeader().getKeyID();

        List<JWK> matches = new JWKSelector(
                new JWKMatcher.Builder()
                        .keyType(keyType)
                        .keyID(keyId)
                        .build()
        ).select(jwkSet);

        if (refreshKeysAttempts > 0 && matches.isEmpty()) {
            this.jwkSet = loadJwkSet(url);
            return matches(jwt, jwkSet, refreshKeysAttempts - 1);
        }
        return matches;
    }

    /**
     *
     * @param jwk A JSON Web Key.
     * @return a JWSVerifier for a JWK.
     */
    protected Optional<JWSVerifier> getVerifier(JWK jwk) {
        if (jwk instanceof RSAKey) {
            RSAKey rsaKey = (RSAKey) jwk;
            return getVerifier(rsaKey::toRSAPublicKey, RSASSAVerifier::new);
        } else if (jwk instanceof ECKey) {
            ECKey ecKey = (ECKey) jwk;
            return getVerifier(ecKey::toECPublicKey, ECDSAVerifier::new);
        }
        return Optional.empty();
    }


    private <T, R extends JWSVerifier> Optional<R> getVerifier(ThrowingSupplier<T, JOSEException> supplier, ThrowingFunction<T, R, JOSEException> consumer) {
        T publicKey = null;
        try {
            publicKey = supplier.get();
        } catch (JOSEException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("JOSEException when retrieving public key", e);
            }
        }
        if (publicKey != null) {
            try {
                return Optional.of(consumer.apply(publicKey));
            } catch (JOSEException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("JOSEException when instantiating the verifier", e);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * returns true if any JWK match is able to verify the JWT signature.
     *
     * @param matches A List of JSON Web key matches.
     * @param jwt A JWT to be verified.
     * @return true if the JWT signature could be verified.
     */
    protected boolean verify(List<JWK> matches, SignedJWT jwt) {
        return matches.stream()
                .map(this::getVerifier)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(verifier -> {
                    try {
                        return jwt.verify(verifier);
                    } catch (JOSEException e) {
                        if (LOG.isErrorEnabled()) {
                            LOG.error("JOSEException when verifying jwt", e);
                        }
                        return false;
                    }
                });
    }
}
