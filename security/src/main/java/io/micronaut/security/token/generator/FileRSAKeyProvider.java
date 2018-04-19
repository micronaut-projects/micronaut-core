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
package io.micronaut.security.token.generator;

import io.micronaut.security.token.reader.BearerTokenReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class FileRSAKeyProvider implements EncryptionKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(BearerTokenReader.class);

    protected final TokenEncryptionConfiguration tokenEncryptionConfiguration;

    private RSAPublicKey publicKey;

    private RSAPrivateKey privateKey;

    public FileRSAKeyProvider(TokenEncryptionConfiguration tokenEncryptionConfiguration) {
        this.tokenEncryptionConfiguration = tokenEncryptionConfiguration;
    }

    @PostConstruct
    void init() {
        if ( tokenEncryptionConfiguration.getPublicKeyPath() != null && tokenEncryptionConfiguration.getPrivateKeyPath() != null) {
            log.debug("Loading public/private key from DER files");
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");

                Path publicKeyPath = tokenEncryptionConfiguration.getPublicKeyPath().toPath();
                byte[] keyBytes = Files.readAllBytes(publicKeyPath);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

                publicKey = (RSAPublicKey) kf.generatePublic(spec);

                Path privateKeyPath = tokenEncryptionConfiguration.getPrivateKeyPath().toPath();
                byte[] privateKeyBytes = Files.readAllBytes(privateKeyPath);
                PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);

                privateKey = (RSAPrivateKey) kf.generatePrivate(privateSpec);

            } catch (InvalidKeySpecException e) {
                log.warn("InvalidKeySpecException while loading public/private key from DER files");

            } catch (NoSuchAlgorithmException e) {
                log.warn("NoSuchAlgorithmException while loading public/private key from DER files");

            } catch (IOException e) {
                log.warn("IOException while loading public/private key from DER files");
            }
        } else if ( tokenEncryptionConfiguration.getPublicKeyPath() == null ) {
            log.warn("public key path is null");
        } else if ( tokenEncryptionConfiguration.getPrivateKeyPath() == null) {
            log.warn("private key path is null");
        }
    }

    @Override
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

}
