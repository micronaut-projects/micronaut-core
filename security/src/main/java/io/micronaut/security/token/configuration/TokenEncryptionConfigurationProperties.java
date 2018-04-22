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

package io.micronaut.security.token.configuration;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import io.micronaut.context.annotation.ConfigurationProperties;

import javax.annotation.Nullable;
import java.io.File;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenEncryptionConfigurationProperties.PREFIX)
public class TokenEncryptionConfigurationProperties implements TokenEncryptionConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".encryption";

    protected boolean enabled = false;
    protected EncryptionMethod encryptionMethod = EncryptionMethod.A128GCM;
    protected JWEAlgorithm jweAlgorithm = JWEAlgorithm.RSA_OAEP_256;
    protected CryptoAlgorithm type = CryptoAlgorithm.RSA;

    @Nullable
    protected String secret;

    @Nullable
    protected File publicKeyPath;

    @Nullable
    protected File privateKeyPath;

    @Override
    public CryptoAlgorithm getType() {
        return this.type;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public EncryptionMethod getEncryptionMethod() {
        return encryptionMethod;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    public JWEAlgorithm getJweAlgorithm() {
        return jweAlgorithm;
    }

    @Override
    public File getPublicKeyPath() {
        return publicKeyPath;
    }

    @Override
    public File getPrivateKeyPath() {
        return privateKeyPath;
    }
}
