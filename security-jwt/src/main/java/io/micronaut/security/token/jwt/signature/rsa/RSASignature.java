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

package io.micronaut.security.token.jwt.signature.rsa;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.security.token.jwt.signature.AbstractSignatureConfiguration;
import javax.validation.constraints.NotNull;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RSA signature configuration.
 * @see <a href="http://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-rsa-signature">JSON Web Token (JWT) with RSA signature</a>
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class RSASignature extends AbstractSignatureConfiguration {

    private RSAPublicKey publicKey;

    private RSAPrivateKey privateKey;

    /**
     *
     * @param config Instance of {@link RSASignatureConfiguration}
     */
    public RSASignature(RSASignatureConfiguration config) {
        if (!supports(config.getJwsAlgorithm())) {
            throw new ConfigurationException(supportedAlgorithmsMessage());
        }
        this.algorithm = config.getJwsAlgorithm();
        this.publicKey = config.getPublicKey();
        this.privateKey = config.getPrivateKey();
    }

    /**
     *
     * @return message explaining the supported algorithms
     */
    @Override
    public String supportedAlgorithmsMessage() {
        return "Only the RS256, RS384, RS512, PS256, PS384 and PS512 algorithms are supported for RSA signature";
    }

    @Override
    public boolean supports(final JWSAlgorithm algorithm) {
        return algorithm != null && RSASSAVerifier.SUPPORTED_ALGORITHMS.contains(algorithm);
    }

    @Override
    public SignedJWT sign(JWTClaimsSet claims) throws JOSEException {
        return signWithPrivateKey(claims, privateKey);
    }

    private SignedJWT signWithPrivateKey(JWTClaimsSet claims, @NotNull  RSAPrivateKey privateKey) throws JOSEException {
        final JWSSigner signer = new RSASSASigner(privateKey);
        final SignedJWT signedJWT = new SignedJWT(new JWSHeader(algorithm), claims);
        signedJWT.sign(signer);
        return signedJWT;
    }

    @Override
    public boolean verify(final SignedJWT jwt) throws JOSEException {
        return verifyWithPublicKey(jwt, this.publicKey);
    }

    private boolean verifyWithPublicKey(final SignedJWT jwt, @NotNull RSAPublicKey publicKey) throws JOSEException {
        final JWSVerifier verifier = new RSASSAVerifier(publicKey);
        return jwt.verify(verifier);
    }
}
