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

package io.micronaut.security.token.jwt.encryption;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import java.text.ParseException;

/**
 * Encryption configuration.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface EncryptionConfiguration {

    /**
     *
     * @return A message indicating the supported algorithms.
     */
    String supportedAlgorithmsMessage();

    /**
     * Whether this encryption configuration supports this algorithm and encryption method.
     *
     * @param algorithm the encryption algorithm
     * @param method the encryption method
     * @return whether this encryption configuration supports this algorithm and encryption method
     */
    boolean supports(JWEAlgorithm algorithm, EncryptionMethod method);

    /**
     * Encrypt a JWT.
     * @throws JOSEException exception when encrypting JWT
     * @throws ParseException exception when encrypting JWT
     * @param jwt the JWT
     * @return the encrypted JWT
     */
    String encrypt(JWT jwt) throws JOSEException, ParseException;

    /**
     * Decrypt an encrypted JWT.
     *
     * @param encryptedJWT the encrypted JWT
     * @throws JOSEException exception when decrypting the JWT
     */
    void decrypt(EncryptedJWT encryptedJWT) throws JOSEException;
}
