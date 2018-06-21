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

package io.micronaut.security.token.jwt.signature.secret;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import io.micronaut.security.token.jwt.signature.AbstractSignatureConfiguration;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class SecretSignature extends AbstractSignatureConfiguration {

    private byte[] secret;

    /**
     *
     * @param config {@link SecretSignatureConfiguration} configuration
     */
    public SecretSignature(SecretSignatureConfiguration config) {
        if (config.getSecret() != null) {
            this.secret = config.isBase64() ? Base64.getDecoder().decode(config.getSecret()) : config.getSecret().getBytes(UTF_8);
        }
        this.algorithm = config.getJwsAlgorithm();
    }

    /**
     *
     * @return message explaining the supported algorithms
     */
    @Override
    public String supportedAlgorithmsMessage() {
        return "Only the HS256, HS384 and HS512 algorithms are supported for HMac signature";
    }

    @Override
    public boolean supports(final JWSAlgorithm algorithm) {
        return algorithm != null && MACVerifier.SUPPORTED_ALGORITHMS.contains(algorithm);
    }

    @Override
    public SignedJWT sign(final JWTClaimsSet claims) throws JOSEException {
        final JWSSigner signer = new MACSigner(this.secret);
        final SignedJWT signedJWT = new SignedJWT(new JWSHeader(algorithm), claims);
        signedJWT.sign(signer);
        return signedJWT;
    }

    @Override
    public boolean verify(final SignedJWT jwt) throws JOSEException {
        final JWSVerifier verifier = new MACVerifier(this.secret);
        return jwt.verify(verifier);
    }

    /**
     *
     * @return a string build the secret byte[] and UTF_8 charset
     */
    public String getSecret() {
        return new String(secret, UTF_8);
    }

    /**
     * Sets secret byte[] with a string with UTF_8 charset.
     * @param secret UTF_8 string
     */
    public void setSecret(final String secret) {
        this.secret = secret.getBytes(UTF_8);
    }
}
