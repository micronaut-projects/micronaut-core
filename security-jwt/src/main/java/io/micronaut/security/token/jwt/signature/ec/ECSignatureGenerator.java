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
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration;

import javax.validation.constraints.NotNull;
import java.security.interfaces.ECPrivateKey;

/**
 * Elliptic curve signature generator. Extends {@link io.micronaut.security.token.jwt.signature.ec.ECSignature} adding the ability to sign JWT.
 *
 * @see <a href="http://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-ec-signature">JSON Web Token (JWT) with EC signature</a>
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class ECSignatureGenerator extends ECSignature implements SignatureGeneratorConfiguration {

    private ECPrivateKey privateKey;

    /**
     *
     * @param config Instance of {@link ECSignatureConfiguration}
     */
    public ECSignatureGenerator(ECSignatureGeneratorConfiguration config) {
        super(config);
        this.privateKey = config.getPrivateKey();
    }

    @Override
    public SignedJWT sign(JWTClaimsSet claims) throws JOSEException {
        return signWithPrivateKey(claims, this.privateKey);
    }

    /**
     *
     * @param claims The JWT Claims
     * @param privateKey The EC Private Key
     * @return A signed JWT
     * @throws JOSEException thrown in the JWT signing
     */
    protected SignedJWT signWithPrivateKey(JWTClaimsSet claims, @NotNull ECPrivateKey privateKey) throws JOSEException {
        final JWSSigner signer = new ECDSASigner(privateKey);
        final SignedJWT signedJWT = new SignedJWT(new JWSHeader(algorithm), claims);
        signedJWT.sign(signer);
        return signedJWT;
    }
}
