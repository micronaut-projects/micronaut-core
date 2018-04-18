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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

@Singleton
public class SignedJwtTokenGenerator extends AbstractTokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(SignedJwtTokenGenerator.class);

    private JWSSigner signer;

    public SignedJwtTokenGenerator(TokenConfiguration tokenConfiguration,
                                   ClaimsGenerator claimsGenerator,
                                   JWTClaimsSetConverter jwtClaimsSetConverter) {
        super(tokenConfiguration, claimsGenerator, jwtClaimsSetConverter);
    }

    @PostConstruct
    void initialize() throws Exception {
        signer = new MACSigner(tokenConfiguration.getSecret());
    }

    @Override
    protected Optional<JWT> generate(Map<String, Object> claims) {
        Optional<JWTClaimsSet> claimsSet = jwtClaimsSetConverter.convert(claims, JWTClaimsSet.class, null);
        if (claimsSet.isPresent()) {
            final JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(tokenConfiguration.getJwsAlgorithm());
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(jwsAlgorithm), claimsSet.get());
            try {
                signedJWT.sign(signer);
            } catch (JOSEException e) {
                log.error("JOSEException signing JWT");
                return Optional.empty();
            }
            return Optional.of(signedJWT);
        }
        return Optional.empty();
    }
}
