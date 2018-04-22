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
import io.micronaut.core.util.Toggleable;

import java.io.File;

/**
 * Represents configuration of the JWT encryption mechanism.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface TokenEncryptionConfiguration extends Toggleable {

   /**
    * @return true if an encrypted JWT should be used
    */
    boolean isEnabled();

   /**
    * @return The algorithm to encrypt the token
    */
    CryptoAlgorithm getType();

    /**
     * @return The path to the public key
     */
    File getPublicKeyPath();

    /**
     * @return The path to the private key
     */
    File getPrivateKeyPath();

    /**
     * @return The JWE algorithm
     */
    JWEAlgorithm getJweAlgorithm();

    /**
     * @return The encryption method
     */
    EncryptionMethod getEncryptionMethod();

    /**
     *
     * @return Secret's length must be at least 256 bits. it is used to sign JWT.
     */
     String getSecret();
}
