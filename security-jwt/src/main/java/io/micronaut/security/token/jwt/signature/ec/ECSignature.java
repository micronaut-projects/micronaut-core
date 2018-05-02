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

package io.micronaut.security.token.jwt.signature.ec;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.security.token.jwt.signature.AbstractSignatureConfiguration;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.validation.constraints.NotNull;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Elliptic curve signature configuration.
 * @see <a href="http://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-ec-signature">JSON Web Token (JWT) with EC signature</a>
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class ECSignature extends AbstractSignatureConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenValidator.class);

    private ECPublicKey publicKey;

    private ECPrivateKey privateKey;

    /**
     *
     * @param config Instance of {@link ECSignatureConfiguration}
     */
    public ECSignature(ECSignatureConfiguration config) {
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
        return "Only the ES256, ES384 and ES512 algorithms are supported for elliptic curve signature";
    }

    @Override
    public boolean supports(final JWSAlgorithm algorithm) {
        return algorithm != null && ECDSAVerifier.SUPPORTED_ALGORITHMS.contains(algorithm);
    }

    @Override
    public SignedJWT sign(JWTClaimsSet claims) throws JOSEException {
        return signWithPrivateKey(claims, this.privateKey);
    }

    private SignedJWT signWithPrivateKey(JWTClaimsSet claims, @NotNull ECPrivateKey privateKey) throws JOSEException {
        final JWSSigner signer = new ECDSASigner(privateKey);
        final SignedJWT signedJWT = new SignedJWT(new JWSHeader(algorithm), claims);
        signedJWT.sign(signer);
        return signedJWT;
    }

    @Override
    public boolean verify(final SignedJWT jwt) throws JOSEException {
        return verify(jwt, this.publicKey);
    }

    private boolean verify(final SignedJWT jwt, @NotNull ECPublicKey publicKey) throws JOSEException {
        final JWSVerifier verifier = new ECDSAVerifier(this.publicKey);
        return jwt.verify(verifier);
    }
}
