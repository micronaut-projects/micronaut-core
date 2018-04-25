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

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import io.micronaut.core.util.Toggleable;
import io.micronaut.security.jwt.config.CryptoAlgorithm;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface JwtEncryptionConfiguration extends Toggleable {

    /**
     * Which kind of encryption this configuration refers to [RSA, EC, Secret].
     * @return Instance of {@link CryptoAlgorithm}
     */
    CryptoAlgorithm getType();

    /**
     *
     * @return Full path to PEM file
     */
    String getPemPath();

    /**
     * @return The JWE algorithm
     */
    JWEAlgorithm getJweAlgorithm();

    /**
     *
     * @return {@link EncryptionMethod}
     */
    EncryptionMethod getEncryptionMethod();

    /**
     *
     * @return Secret string which will be used when type is {@link CryptoAlgorithm#SECRET}
     */
    String getSecret();
}
