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
package io.micronaut.security.token;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import io.micronaut.security.jwt.EncryptionConfig;
import org.pac4j.jwt.config.encryption.ECEncryptionConfiguration;
import org.pac4j.jwt.config.encryption.EncryptionConfiguration;
import java.security.KeyPair;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class JwtEncryptionConfigurationUtil {
    public static EncryptionConfiguration createEncryptionConfiguration(FileRSAKeyProvider fileRSAKeyProvider, EncryptionConfig config) {
        KeyPair ecKeyPair = new KeyPair(fileRSAKeyProvider.getPublicKey(), fileRSAKeyProvider.getPrivateKey());
        ECEncryptionConfiguration encConfig = new ECEncryptionConfiguration(ecKeyPair);
        encConfig.setAlgorithm(JWEAlgorithm.parse(config.getJweAlgorithm()));
        encConfig.setMethod(EncryptionMethod.parse(config.getEncryptionMethod()));
        return encConfig;
    }
}
