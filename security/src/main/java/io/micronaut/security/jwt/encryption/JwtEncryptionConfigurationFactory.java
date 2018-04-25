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

package io.micronaut.security.jwt.encryption;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class JwtEncryptionConfigurationFactory {

    /**
     * Instantiates a {@link RSAEncryptionConfiguration}, {@link ECEncryptionConfiguration} or {@link SecretEncryptionConfiguration} depending on {@link JwtEncryptionConfiguration}.
     * @param jwtEncryptionConfiguration Instance of {@link JwtEncryptionConfiguration}
     * @return An object conforming to {@link EncryptionConfiguration}
     */
    public static EncryptionConfiguration create(JwtEncryptionConfiguration jwtEncryptionConfiguration) {
        switch (jwtEncryptionConfiguration.getType()) {
            case RSA:
                return new RSAEncryptionConfiguration(jwtEncryptionConfiguration);

            case EC:
                return new ECEncryptionConfiguration(jwtEncryptionConfiguration);

            default:
            case SECRET:
                return new SecretEncryptionConfiguration(jwtEncryptionConfiguration);
        }
    }
}
