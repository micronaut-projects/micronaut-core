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

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenEncryptionConfigurationProperties.PREFIX)
public class TokenEncryptionConfigurationProperties implements TokenEncryptionConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".encryption";

    public static final String DEFAULT_ENCRYPTIONMETHOD = "A128GCM";

    public static final String DEFAULT_JWEALGORITHM = "RSA-OAEP";

    private boolean enabled = false;

    private String encryptionMethod = DEFAULT_ENCRYPTIONMETHOD;

    private String jweAlgorithm = DEFAULT_JWEALGORITHM;

    /** Full path to the public key so that {@code new File(publicKeyPath).exists() == true} */
    @Nullable
    private String publicKeyPath;

    /** Full path to the private key so that {@code new File(publicKeyPath).exists() == true} */
    @Nullable
    private String privateKeyPath;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return The Encryption Method
     */
    @Override
    public String getEncryptionMethod() {
        return encryptionMethod;
    }

    /**
     * @return The JWS Algorithm
     */
    public void setEncryptionMethod(@Nullable String encryptionMethod) {
        if ( encryptionMethod == null ) {
            this.encryptionMethod = DEFAULT_ENCRYPTIONMETHOD;
        } else {
            if ( !validEncryptionMethods().contains(encryptionMethod) ) {
                throw new IllegalArgumentException(invalidEncryptionMethodMessage(encryptionMethod));
            }
            this.encryptionMethod = encryptionMethod;
        }
    }

    protected String invalidEncryptionMethodMessage(String jwsAlgorithm) {
        StringBuilder sb = new StringBuilder();
        sb.append("Encryption Method: ");
        sb.append(jwsAlgorithm);
        sb.append("not allowed. Valid values: ");
        sb.append(validEncryptionMethods().stream().reduce((a, b) -> a + "," + b).get());
        return sb.toString();
    }

    protected List<String> validEncryptionMethods() {
        return Arrays.asList("A128CBC-HS256",
                "A192CBC-HS384",
                "A256CBC-HS512",
                "A128CBC+HS256",
                "A256CBC+HS512",
                DEFAULT_ENCRYPTIONMETHOD,
                "A192GCM",
                "A256GCM");
    }

    /**
     * @return The JWE Algorithm
     */
    @Override
    public String getJweAlgorithm() {
        return jweAlgorithm;
    }

    public void setJweAlgorithm(@Nullable String jweAlgorithm) {
        if ( jweAlgorithm == null ) {
            this.jweAlgorithm = DEFAULT_JWEALGORITHM;
        } else {
            if ( !validJweAlgorithms().contains(jweAlgorithm) ) {
                throw new IllegalArgumentException(invalidJweMessage(jweAlgorithm));
            }
            this.jweAlgorithm = jweAlgorithm;
        }
    }

    protected String invalidJweMessage(String jweAlgorithm) {
        StringBuilder sb = new StringBuilder();
        sb.append("JWE Algorithm: ");
        sb.append(jweAlgorithm);
        sb.append("not allowed. Valid values: ");
        sb.append(validJweAlgorithms().stream().reduce((a, b) -> a + "," + b).get());
        return sb.toString();
    }

    protected List<String> validJweAlgorithms() {
        return Arrays.asList("RSA1_5",
                DEFAULT_JWEALGORITHM,
                "RSA-OAEP-256",
                "A128KW",
                "A192KW",
                "A256KW",
                "dir",
                "ECDH-ES",
                "ECDH-ES+A128KW",
                "ECDH-ES+A192KW",
                "ECDH-ES+A256KW",
                "A128GCMKW",
                "A192GCMKW",
                "A256GCMKW",
                "PBES2-HS256+A128KW",
                "PBES2-HS384+A192KW",
                "PBES2-HS512+A256KW");
    }

    public void setPublicKeyPath(String publicKeyPath) {
        if ( publicKeyPath  != null) {
            if ( !new File(publicKeyPath).exists() ) {
                StringBuilder sb = new StringBuilder();
                sb.append("Public Key path ");
                sb.append(publicKeyPath);
                sb.append("does not exist");
                String msg = sb.toString();
                new IllegalArgumentException(msg);
            }
            this.publicKeyPath = publicKeyPath;
        }
    }

    public String getPublicKeyPath() {
        return this.publicKeyPath;
    }

    public String getPrivateKeyPath() {
        return this.privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        if ( privateKeyPath  != null) {
            if ( !new File(privateKeyPath).exists() ) {
                StringBuilder sb = new StringBuilder();
                sb.append("Private Key path ");
                sb.append(privateKeyPath);
                sb.append("does not exist");
                String msg = sb.toString();
                new IllegalArgumentException(msg);
            }
            this.privateKeyPath = privateKeyPath;
        }
    }
}
