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

package io.micronaut.security.token.generator;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.BeanInstantiationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

@Singleton
@Requires(property = TokenEncryptionConfigurationProperties.PREFIX + ".enabled", notEquals = "true")
public class SignedJwtTokenGenerator extends AbstractTokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(SignedJwtTokenGenerator.class);

    private JWSSigner signer;

    public SignedJwtTokenGenerator(TokenConfiguration tokenConfiguration,
                                   JWTClaimsSetGenerator claimsGenerator) throws KeyLengthException {
        super(tokenConfiguration, claimsGenerator);
        signer = createSigner(tokenConfiguration);
    }

    protected JWSSigner createSigner(TokenConfiguration tokenConfiguration) throws KeyLengthException {
        return new MACSigner(tokenConfiguration.getSecret());
    }

    @Override
    protected JWT generate(Map<String, Object> claims) throws JOSEException {
        JWTClaimsSet claimsSet = claimsGenerator.generateClaimsSet(claims);
        final JWSAlgorithm jwsAlgorithm = tokenConfiguration.getJwsAlgorithm();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(jwsAlgorithm), claimsSet);
        signedJWT.sign(signer);
        return signedJWT;
    }
}
