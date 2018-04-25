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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import io.micronaut.security.jwt.KeyPairProvider;
import javax.validation.constraints.NotNull;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Optional;

/**
 * Elliptic curve encryption configuration.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class ECEncryptionConfiguration extends AbstractEncryptionConfiguration {

    private ECPublicKey publicKey;

    private ECPrivateKey privateKey;

    /**
     *
     * @param jwtEncryptionConfiguration Instance of {@link JwtEncryptionConfiguration}
     */
    public ECEncryptionConfiguration(JwtEncryptionConfiguration jwtEncryptionConfiguration) {
        this.method = jwtEncryptionConfiguration.getEncryptionMethod();
        this.algorithm = jwtEncryptionConfiguration.getJweAlgorithm();

        if (jwtEncryptionConfiguration.getPemPath() != null) {
            Optional<KeyPair> keyPair = KeyPairProvider.keyPair(jwtEncryptionConfiguration.getPemPath());
            keyPair.ifPresent(this::setKeyPair);
        }
    }

    @Override
    public boolean supports(final JWEAlgorithm algorithm, final EncryptionMethod method) {
        if (algorithm != null && method != null) {
            return ECDHDecrypter.SUPPORTED_ALGORITHMS.contains(algorithm) && ECDHDecrypter.SUPPORTED_ENCRYPTION_METHODS.contains(method);
        }
        return false;
    }

    /**
     *
     * @return message explaining the supported algorithms
     */
    @Override
    public String supportedAlgorithmsMessage() {
        return "Only Elliptic-curve algorithms are supported with the appropriate encryption method";
    }

    @Override
    protected JWEEncrypter buildEncrypter() throws JOSEException {
        return buildEncrypterWithPublicKey(this.publicKey);
    }

    /**
     * Instantiates {@link ECDHEncrypter} with {@link ECPublicKey}.
     * @param publicKey Instance of {@link ECPublicKey}
     * @return Instance of {@link ECDHEncrypter}
     * @throws JOSEException if the {@link ECDHEncrypter} cannot be intantiated
     */
    protected JWEEncrypter buildEncrypterWithPublicKey(@NotNull ECPublicKey publicKey) throws JOSEException {
        return new ECDHEncrypter(publicKey);
    }

    @Override
    protected JWEDecrypter buildDecrypter() throws JOSEException {
        return buildDecrypterWithPrivateKey(this.privateKey);
    }

    private JWEDecrypter buildDecrypterWithPrivateKey(ECPrivateKey privateKey) throws JOSEException {
        return new ECDHDecrypter(privateKey);
    }

    /**
     * Sets public and private key with a KeyPair.
     * @param keyPair Instance of {@link KeyPair}
     */
    public void setKeyPair(@NotNull final KeyPair keyPair) {
        this.privateKey = (ECPrivateKey) keyPair.getPrivate();
        this.publicKey = (ECPublicKey) keyPair.getPublic();
    }
}
