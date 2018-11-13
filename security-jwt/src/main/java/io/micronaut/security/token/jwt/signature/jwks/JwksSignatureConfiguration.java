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
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Signature configuration which enables verification of remote JSON Web Key Set.
 *
 * A bean of this class is created for each {@link io.micronaut.security.token.jwt.signature.jwks.JwksSignatureConfigurationProperties}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@EachBean(JwksSignatureConfigurationProperties.class)
public class JwksSignatureConfiguration implements SignatureConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JwksSignatureConfiguration.class);
    private static final int REFRESH_JWKS_ATTEMPTS = 1;
    private JWKSet jwkSet;
    private KeyType keyType;
    private String url;

    /**
     *
     * @param jwksSignatureConfigurationProperties JSON Web Key Set configuration.
     */
    public JwksSignatureConfiguration(JwksSignatureConfigurationProperties jwksSignatureConfigurationProperties) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("JWT validation URL: {}", jwksSignatureConfigurationProperties.getUrl());
        }
        this.url = jwksSignatureConfigurationProperties.getUrl();
        this.jwkSet = jwkSetByUrl(jwksSignatureConfigurationProperties.getUrl());
        this.keyType = jwksSignatureConfigurationProperties.getKeyType();
    }


    /**
     *
     * @return A message indicating the supported algorithms.
     */
    @Override
    public String supportedAlgorithmsMessage() {
        Optional<String> csv = jwkSet.getKeys().stream()
                .map(JWK::getAlgorithm)
                .map(Algorithm::getName)
                .reduce((a, b) -> a + ", " + b);
        return "Only the " + (csv.isPresent() ? csv.get() : "") + " algorithms are supported";
    }

    /**
     * Whether this signature configuration supports this algorithm.
     *
     * @param algorithm the signature algorithm
     * @return whether this signature configuration supports this algorithm
     */
    @Override
    public boolean supports(JWSAlgorithm algorithm) {
        return jwkSet.getKeys().stream()
                .map(JWK::getAlgorithm)
                .anyMatch(jkwAlgorithm -> algorithm.getName().equals(jkwAlgorithm.getName()));
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

        List<JWK> matches = matches(jwt, jwkSet, REFRESH_JWKS_ATTEMPTS);
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
    protected JWKSet jwkSetByUrl(String url) {
        try {
            return JWKSet.load(new URL(url));
        } catch (FileNotFoundException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("FileNotFoundException loading JWK", e);
            }
        } catch (IOException | ParseException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Exception loading JWK", e);
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
    protected List<JWK> matches(SignedJWT jwt, JWKSet jwkSet, int refreshKeysAttempts) {
        if (jwkSet == null) {
            return Collections.emptyList();
        }
        String keyId = jwt.getHeader().getKeyID();
        if (keyId == null) {
            return Collections.emptyList();
        }
        List<JWK> matches = new JWKSelector(
                new JWKMatcher.Builder()
                        .keyType(keyType)
                        .keyID(keyId)
                        .build()
        ).select(jwkSet);

        if (refreshKeysAttempts > 0 && matches.isEmpty()) {
            this.jwkSet = jwkSetByUrl(url);
            return matches(jwt, jwkSet, (refreshKeysAttempts - 1));
        }
        return matches;
    }

    /**
     *
     * @param jwk A JSON Web Key.
     * @return a JWSVerifier for a JWK.
     */
    protected JWSVerifier verifiersForJwk(JWK jwk) {
        if (jwk instanceof RSAKey) {
            return jwsVerifierForRSAKey(((RSAKey) jwk));

        } else if (jwk instanceof ECKey) {
            return jwsVerifierForECKey((ECKey) jwk);
        }
        return null;
    }

    /**
     *
     * @param rsaKey A RSA Key.
     * @return a JWSVerifier for a RSAKey.
     */
    protected JWSVerifier jwsVerifierForRSAKey(RSAKey rsaKey) {
        RSAPublicKey rsaPublicKey = null;
        try {
            rsaPublicKey = rsaKey.toRSAPublicKey();
        } catch (JOSEException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("JOSEException when retrieving RSA Public Key", e);
            }
        }
        if (rsaPublicKey == null) {
            return null;
        }
        return new RSASSAVerifier(rsaPublicKey);
    }

    /**
     *
     * @param ecKey A ECKey
     * @return a JWSVerifier for a ECKey.
     */
    protected JWSVerifier jwsVerifierForECKey(ECKey ecKey) {
        ECPublicKey ecPublicKey = null;
        try {
            ecPublicKey = ecKey.toECPublicKey();
        } catch (JOSEException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("JOSEException when retrieving EC Public Key", e);
            }
        }
        if (ecPublicKey == null) {
            return null;
        }
        try {
            return new ECDSAVerifier(ecPublicKey);
        } catch (JOSEException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("JOSEException when instantiating ECDSAVerifier", e);
            }
        }
        return null;
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
                .map(this::verifiersForJwk)
                .filter(Objects::nonNull)
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
