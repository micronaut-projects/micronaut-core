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

package io.micronaut.security.token.jwt.encryption.rsa;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.crypto.RSADecrypter;
import com.nimbusds.jose.crypto.RSAEncrypter;
import io.micronaut.security.token.jwt.encryption.AbstractEncryptionConfiguration;
import javax.validation.constraints.NotNull;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * RSA encryption configuration.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class RSAEncryption extends AbstractEncryptionConfiguration {

    private RSAPublicKey publicKey;

    private RSAPrivateKey privateKey;

    /**
     *
     * @param rsaEncryptionConfiguration Instance of {@link RSAEncryptionConfiguration}
     */
    public RSAEncryption(RSAEncryptionConfiguration rsaEncryptionConfiguration) {
        this.method = rsaEncryptionConfiguration.getEncryptionMethod();
        this.algorithm = rsaEncryptionConfiguration.getJweAlgorithm();
        this.publicKey = rsaEncryptionConfiguration.getPublicKey();
        this.privateKey = rsaEncryptionConfiguration.getPrivateKey();
    }

    @Override
    public boolean supports(final JWEAlgorithm algorithm, final EncryptionMethod method) {
        if (algorithm != null && method != null) {
            return RSADecrypter.SUPPORTED_ALGORITHMS.contains(algorithm) && RSADecrypter.SUPPORTED_ENCRYPTION_METHODS.contains(method);
        }
        return false;
    }

    /**
     *
     * @return message explaining the supported algorithms
     */
    @Override
    public String supportedAlgorithmsMessage() {
        return "Only RSA algorithms are supported with the appropriate encryption method";
    }

    @Override
    protected JWEEncrypter buildEncrypter() {
        return buildEncrypterWithPublicKey(this.publicKey);
    }

    private JWEEncrypter buildEncrypterWithPublicKey(@NotNull RSAPublicKey publicKey) {
        return new RSAEncrypter(publicKey);
    }

    @Override
    protected JWEDecrypter buildDecrypter() {
        return buildDecrypterWithPrivateKey(this.privateKey);
    }

    private JWEDecrypter buildDecrypterWithPrivateKey(@NotNull RSAPrivateKey privateKey) {
        return new RSADecrypter(privateKey);
    }
}
