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

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

@Singleton
public class EncryptedJwtTokenGenerator extends AbstractTokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(EncryptedJwtTokenGenerator.class);

    protected final TokenEncryptionConfiguration tokenEncryptionConfiguration;

    protected final RSAKeyProvider rsaKeyProvider;

    private RSAEncrypter encrypter;
    private JWEHeader header;

    public EncryptedJwtTokenGenerator(TokenConfiguration tokenConfiguration,
                                  ClaimsGenerator claimsGenerator,
                                  JWTClaimsSetConverter jwtClaimsSetConverter,
                                  TokenEncryptionConfiguration tokenEncryptionConfiguration,
                                  RSAKeyProvider rsaKeyProvider) {
        super(tokenConfiguration, claimsGenerator, jwtClaimsSetConverter);
        this.tokenEncryptionConfiguration = tokenEncryptionConfiguration;
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @PostConstruct
    void initialize() {
        final JWEAlgorithm jweAlgorithm = JWEAlgorithm.parse(tokenEncryptionConfiguration.getJweAlgorithm());
        EncryptionMethod encryptionMethod = EncryptionMethod.parse(tokenEncryptionConfiguration.getEncryptionMethod());
        this.header = new JWEHeader(jweAlgorithm, encryptionMethod);
        // Create an encrypter with the specified public RSA key
        this.encrypter = new RSAEncrypter(rsaKeyProvider.getPublicKey());
    }

    @Override
    protected Optional<JWT> generate(Map<String, Object> claims) {

        // Create the encrypted JWT object
        Optional<JWTClaimsSet> claimsSet = jwtClaimsSetConverter.convert(claims, JWTClaimsSet.class, null);

        if ( claimsSet.isPresent() ) {

            EncryptedJWT jwt = new EncryptedJWT(header, claimsSet.get());

            // Do the actual encryption
            try {
                jwt.encrypt(encrypter);

            } catch (JOSEException e) {
                log.error("JOSEException encrypting JWT");
            }

            return Optional.of(jwt);
        }
        return Optional.empty();
    }
}
