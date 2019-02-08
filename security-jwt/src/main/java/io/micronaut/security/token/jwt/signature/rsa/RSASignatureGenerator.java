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
package io.micronaut.security.token.jwt.signature.rsa;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.security.token.jwt.endpoints.JwkProvider;
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration;

import javax.annotation.Nonnull;
import java.security.interfaces.RSAPrivateKey;

/**
 * RSA signature Generator. Expands {@link RSASignature} to add methods to sign JWT.
 * @see <a href="http://connect2id.com/products/nimbus-jose-jwt/examples/jwt-with-rsa-signature">JSON Web Token (JWT) with RSA signature</a>
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class RSASignatureGenerator extends RSASignature implements SignatureGeneratorConfiguration {
    private RSAPrivateKey privateKey;
    private String keyId;

    /**
     * @param config Instance of {@link RSASignatureConfiguration}
     */
    public RSASignatureGenerator(RSASignatureGeneratorConfiguration config) {
        super(config);
        if (!supports(config.getJwsAlgorithm())) {
            throw new ConfigurationException(supportedAlgorithmsMessage());
        }
        this.algorithm = config.getJwsAlgorithm();
        this.privateKey = config.getPrivateKey();
        if (config instanceof JwkProvider) {
            this.keyId = ((JwkProvider) config).retrieveJsonWebKey().getKeyID();
        }
    }

    @Override
    public SignedJWT sign(JWTClaimsSet claims) throws JOSEException {
        return signWithPrivateKey(claims, privateKey);
    }

    /**
     *
     * @param claims The JWT Claims
     * @param privateKey The RSA Private Key
     * @return A signed JWT
     * @throws JOSEException thrown in the JWT signing
     */
    protected SignedJWT signWithPrivateKey(JWTClaimsSet claims, @Nonnull RSAPrivateKey privateKey) throws JOSEException {
        final JWSSigner signer = new RSASSASigner(privateKey);
        JWSHeader jwsHeader = new JWSHeader.Builder(algorithm).keyID(keyId).build();
        final SignedJWT signedJWT = new SignedJWT(jwsHeader, claims);
        signedJWT.sign(signer);
        return signedJWT;
    }
}
