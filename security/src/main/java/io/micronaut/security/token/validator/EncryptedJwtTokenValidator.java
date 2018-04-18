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
package io.micronaut.security.token.validator;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.generator.RSAKeyProvider;
import io.micronaut.security.token.generator.TokenConfiguration;
import io.micronaut.security.token.generator.TokenEncryptionConfiguration;
import io.micronaut.security.token.generator.TokenEncryptionConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.text.ParseException;
import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Requires(property = TokenEncryptionConfigurationProperties.PREFIX + ".enabled")
public class EncryptedJwtTokenValidator extends AbstractTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(EncryptedJwtTokenValidator.class);

    protected final TokenEncryptionConfiguration tokenEncryptionConfiguration;
    protected final RSAKeyProvider rsaKeyProvider;

    public EncryptedJwtTokenValidator(TokenConfiguration tokenConfiguration,
                               TokenEncryptionConfiguration tokenEncryptionConfiguration,
                                RSAKeyProvider rsaKeyProvider) {
        super(tokenConfiguration);
        this.tokenEncryptionConfiguration = tokenEncryptionConfiguration;
        this.rsaKeyProvider = rsaKeyProvider;
    }

    private RSADecrypter rsaDecrypter;

    @PostConstruct
    public void initialize() {
        rsaDecrypter = new RSADecrypter(rsaKeyProvider.getPrivateKey());
    }

    @Override
    public Map<String, Object> validateTokenAndGetClaims(String token) {
        try {
            JWT jwt = JWTParser.parse(token);
            if (jwt instanceof EncryptedJWT) {
                EncryptedJWT encryptedJWT = (EncryptedJWT) jwt;
                encryptedJWT.decrypt(rsaDecrypter);
                return encryptedJWT.getJWTClaimsSet().getClaims();
            } else {
                return null;
            }

        } catch(ParseException e) {
            log.warn("ParseException parsing token: {}", token);

        } catch (JOSEException e) {
            log.warn("JOSEException while decrypting {}", token);
        }
        return null;
    }
}
