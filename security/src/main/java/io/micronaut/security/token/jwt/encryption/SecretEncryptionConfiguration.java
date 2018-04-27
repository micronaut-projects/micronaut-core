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
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Secret encryption configuration.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class SecretEncryptionConfiguration extends AbstractEncryptionConfiguration {

    private byte[] secret;

    /**
     *
     * @param jwtEncryptionConfiguration Instance of {@link JwtEncryptionConfiguration}
     */
    public SecretEncryptionConfiguration(JwtEncryptionConfiguration jwtEncryptionConfiguration) {
        final String secret = jwtEncryptionConfiguration.getSecret();
        if (secret != null) {
            this.secret = secret.getBytes(UTF_8);
        }
        if (supports(jwtEncryptionConfiguration.getJweAlgorithm(), jwtEncryptionConfiguration.getEncryptionMethod())) {
            this.method = jwtEncryptionConfiguration.getEncryptionMethod();
            this.algorithm = jwtEncryptionConfiguration.getJweAlgorithm();
        } else {
            algorithm = JWEAlgorithm.DIR;
            method = EncryptionMethod.A256GCM;
        }
    }

    @Override
    public boolean supports(final JWEAlgorithm algorithm, final EncryptionMethod method) {
        if (algorithm != null && method != null) {
            final boolean isDirect = DirectDecrypter.SUPPORTED_ALGORITHMS.contains(algorithm)
                    && DirectDecrypter.SUPPORTED_ENCRYPTION_METHODS.contains(method);
            final boolean isAes = AESDecrypter.SUPPORTED_ALGORITHMS.contains(algorithm)
                    && AESDecrypter.SUPPORTED_ENCRYPTION_METHODS.contains(method);
            return isDirect || isAes;
        }
        return false;
    }

    /**
     *
     * @return message explaining the supported algorithms
     */
    @Override
    public String supportedAlgorithmsMessage() {
        return "Only the direct and AES algorithms are supported with the appropriate encryption method";
    }

    @Override
    protected JWEEncrypter buildEncrypter() throws KeyLengthException {
        if (DirectDecrypter.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return new DirectEncrypter(this.secret);
        } else {
            return new AESEncrypter(this.secret);
        }
    }

    @Override
    protected JWEDecrypter buildDecrypter() throws KeyLengthException {
        if (DirectDecrypter.SUPPORTED_ALGORITHMS.contains(algorithm)) {
            return new DirectDecrypter(this.secret);
        } else {
            return new AESDecrypter(this.secret);
        }
    }

    /**
     *
     * @return a string build the secret byte[] and UTF_8 charset
     */
    public String getSecret() {
        return new String(secret, UTF_8);
    }


    /**
     * Sets secret byte[] with a string with UTF_8 charset.
     * @param secret UTF_8 string
     */
    public void setSecret(final String secret) {
        this.secret = secret.getBytes(UTF_8);
    }
}
