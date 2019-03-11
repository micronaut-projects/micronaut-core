/*
 * Copyright 2017-2019 original authors
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
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.security.token.jwt.signature.AbstractSignatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.security.interfaces.ECPublicKey;

/**
 * Elliptic curve signature. Adds method to verify signed JWT.
 * @see <a href="http://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-ec-signature">JSON Web Token (JWT) with EC signature</a>
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class ECSignature extends AbstractSignatureConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ECSignature.class);

    private ECPublicKey publicKey;

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
    public boolean verify(final SignedJWT jwt) throws JOSEException {
        return verify(jwt, this.publicKey);
    }

    private boolean verify(final SignedJWT jwt, @Nonnull ECPublicKey publicKey) throws JOSEException {
        final JWSVerifier verifier = new ECDSAVerifier(this.publicKey);
        return jwt.verify(verifier);
    }
}
