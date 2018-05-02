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

package io.micronaut.security.token.jwt.encryption.secret;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration;

import javax.inject.Singleton;

/**
 * Creates {@link EncryptionConfiguration} for each {@link SecretEncryptionConfiguration} bean.
 * @author Sergio del Amo
 * @since 1.0
 */
@Factory
public class SecretEncryptionFactory {

    /**
     *
     * @param configuration {@link SecretEncryptionConfiguration} bean.
     * @return The {@link EncryptionConfiguration}
     */
    @EachBean(SecretEncryptionConfiguration.class)
    @Singleton
    public EncryptionConfiguration encryptionConfiguration(SecretEncryptionConfiguration configuration) {
        return new SecretEncryption(configuration);
    }
}
