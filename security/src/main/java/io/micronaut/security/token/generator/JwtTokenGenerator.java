/*
 * Copyright 2017 original authors
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

import io.micronaut.security.authentication.AuthenticationSuccess;
import io.micronaut.security.config.JwtConfiguration;
import io.micronaut.security.token.FileRSAKeyProvider;
import io.micronaut.security.token.JwtEncryptionConfigurationUtil;
import io.micronaut.security.token.JwtSignatureConfigurationUtil;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.profile.JwtGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class JwtTokenGenerator implements TokenGenerator {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenGenerator.class);

    protected final JwtConfiguration jwtConfiguration;
    protected final FileRSAKeyProvider fileRSAKeyProvider;
    protected final ClaimsGenerator claimsGenerator;

    private JwtGenerator<CommonProfile> jwtGenerator;

    public JwtTokenGenerator(JwtConfiguration jwtConfiguration,
                             FileRSAKeyProvider fileRSAKeyProvider,
                             ClaimsGenerator claimsGenerator) {
        this.jwtConfiguration = jwtConfiguration;
        this.fileRSAKeyProvider = fileRSAKeyProvider;
        this.claimsGenerator = claimsGenerator;
    }

    public JwtGenerator getJwtGenerator() {
        if ( jwtGenerator == null ) {

            final SecretSignatureConfiguration signatureConfiguration = JwtSignatureConfigurationUtil.createSignatureConfiguration(jwtConfiguration);

            if ( jwtConfiguration.isUseEncryptedJwt() ) {
                final EncryptionConfiguration encConfig = JwtEncryptionConfigurationUtil.createEncryptionConfiguration(fileRSAKeyProvider, jwtConfiguration);
                jwtGenerator = new JwtGenerator<>(signatureConfiguration, encConfig);
            } else {
                jwtGenerator = new JwtGenerator<>(signatureConfiguration);
            }
        }
        return jwtGenerator;
    }

    @Override
    public String generateToken(AuthenticationSuccess authenticationSuccess, Integer expiration) {
        Map<String, Object> claims = claimsGenerator.generateClaims(authenticationSuccess, expiration);
        return getJwtGenerator().generate(claims);
    }

    @Override
    public String generateToken(Map<String, Object> claims) {
        return getJwtGenerator().generate(claims);
    }
}
