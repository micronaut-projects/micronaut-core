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
package io.micronaut.security.jwt;

import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;
import org.pac4j.jwt.credentials.authenticator.JwtAuthenticator;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class JwtTokenValidator implements TokenValidator {

    protected final JwtConfiguration jwtConfiguration;
    protected final FileRSAKeyProvider fileRSAKeyProvider;

    private JwtAuthenticator jwtAuthenticator;

    public JwtTokenValidator(JwtConfiguration jwtConfiguration, FileRSAKeyProvider fileRSAKeyProvider) {
        this.jwtConfiguration = jwtConfiguration;
        this.fileRSAKeyProvider = fileRSAKeyProvider;
    }

    public JwtAuthenticator getJwtAuthenticator() {
        if ( jwtAuthenticator == null ) {
            jwtAuthenticator = new JwtAuthenticator();
            final SecretSignatureConfiguration signatureConfiguration = JwtSignatureConfigurationUtil.createSignatureConfiguration(jwtConfiguration);
            jwtAuthenticator.addSignatureConfiguration(signatureConfiguration);

            if ( jwtConfiguration.isUseEncryptedJwt() ) {
                final EncryptionConfiguration encConfig = JwtEncryptionConfigurationUtil.createEncryptionConfiguration(fileRSAKeyProvider, jwtConfiguration);
                jwtAuthenticator.addEncryptionConfiguration(encConfig);
            }
        }
        return jwtAuthenticator;
    }

    @Override
    public Map<String, Object> validateTokenAndGetClaims(String token) {
        CommonProfile profile = getJwtAuthenticator().validateToken(token);
        if ( profile != null ) {
            Map<String, Object> claims = new HashMap(profile.getAttributes());
            claims.put("sub", profile.getId());
            return claims;
        }
        return null;
    }

}
