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
import com.nimbusds.jose.crypto.RSAEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.context.annotation.Requires;
import javax.inject.Singleton;
import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(property = TokenEncryptionConfigurationProperties.PREFIX + ".enabled")
public class EncryptedJwtTokenGenerator extends AbstractTokenGenerator {

    private JWEEncrypter encrypter;
    private JWEHeader header;

    /**
     *
     * @param tokenConfiguration Instance of {@link TokenConfiguration}
     * @param claimsGenerator Instance of {@link JWTClaimsSetGenerator}
     * @param tokenEncryptionConfiguration Instance of {@link TokenEncryptionConfiguration}
     * @param keyProvider Instance of {@link EncryptionKeyProvider}
     */
    public EncryptedJwtTokenGenerator(TokenConfiguration tokenConfiguration,
                                      JWTClaimsSetGenerator claimsGenerator,
                                      TokenEncryptionConfiguration tokenEncryptionConfiguration,
                                      EncryptionKeyProvider keyProvider) {
        super(tokenConfiguration, claimsGenerator);
        this.header = createHeader(tokenEncryptionConfiguration);
        this.encrypter = createEncrypter(keyProvider);
    }

    /**
     * Instantiates a {@link JWEHeader}.
     * @param tokenEncryptionConfiguration {@link TokenEncryptionConfiguration}
     * @return a {@link JWEHeader}.
     */
    protected JWEHeader createHeader(TokenEncryptionConfiguration tokenEncryptionConfiguration) {
        final JWEAlgorithm jweAlgorithm = tokenEncryptionConfiguration.getJweAlgorithm();
        EncryptionMethod encryptionMethod = tokenEncryptionConfiguration.getEncryptionMethod();
        return new JWEHeader(jweAlgorithm, encryptionMethod);
    }

    /**
     *
     * @param keyProvider a public / private key provider
     * @return {@link JWEEncrypter}
     */
    protected JWEEncrypter createEncrypter(EncryptionKeyProvider keyProvider) {
        return new RSAEncrypter(keyProvider.getPublicKey());
    }

    /**
     *
     * @param claims Claims to be included in the JWT
     * @return
     * @throws JOSEException
     */
    @Override
    protected JWT generate(Map<String, Object> claims) throws JOSEException {
        // Create the encrypted JWT object
        JWTClaimsSet claimsSet = claimsGenerator.generateClaimsSet(claims);
        EncryptedJWT jwt = new EncryptedJWT(header, claimsSet);
        // Do the actual encryption
        jwt.encrypt(encrypter);
        return jwt;
    }
}
