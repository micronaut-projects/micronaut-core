/*
 * Copyright 2017 original authors
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
package io.micronaut.security.jwt;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.SecurityConfiguration;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Stores configuration for JWT
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(JwtConfiguration.PREFIX)
public class JwtConfiguration implements SignatureConfiguration, EncryptionConfig {

    public static final String PREFIX = SecurityConfiguration.PREFIX + ".jwt";

    private boolean useEncryptedJwt = false;

    private static final String DEFAULT_ROLES_CLAIM_NAME = "roles";

    private String rolesClaimName = DEFAULT_ROLES_CLAIM_NAME;

    private static final String DEFAULT_JWSALGORITHM = "HS256";

    private String jwsAlgorithm = DEFAULT_JWSALGORITHM;

    private static final String DEFAULT_ENCRYPTIONMETHOD = "A128GCM";

    private String encryptionMethod = DEFAULT_ENCRYPTIONMETHOD;

    private static final String DEFAULT_JWEALGORITHM = "RSA-OAEP";

    private String jweAlgorithm = DEFAULT_JWEALGORITHM;

    private Integer DEFAULT_EXPIRATION = 3600;

    Integer defaultExpiration = DEFAULT_EXPIRATION;

    Integer refreshTokenExpiration = null;

    /** Full path to the public key so that {@code new File(publicKeyPath).exists() == true} */
    @Nullable
    private String publicKeyPath;

    /** Full path to the private key so that {@code new File(publicKeyPath).exists() == true} */
    @Nullable
    private String privateKeyPath;

    @NotBlank
    private String secret;

    public Integer getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public Integer getDefaultExpiration() {
        return defaultExpiration;
    }

    public String getRolesClaimName() {
        return rolesClaimName;
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

    public String getSecret() {
        return this.secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean isUseEncryptedJwt() {
        return useEncryptedJwt;
    }

    public void setUseEncryptedJwt(boolean useEncryptedJwt) {
        this.useEncryptedJwt = useEncryptedJwt;
    }

    /**
     * @return The JWS Algorithm
     */
    public String getJwsAlgorithm() {
        return jwsAlgorithm;
    }

    /**
     * @return The JWS Algorithm
     */
    public void setJwsAlgorithm(@Nullable String jwsAlgorithm) {
        if ( jwsAlgorithm == null ) {
            this.jwsAlgorithm = DEFAULT_JWSALGORITHM;
        } else {
            if ( !validJwsAlgorithms().contains(jwsAlgorithm) ) {
                throw new IllegalArgumentException(invalidJwsMessage(jwsAlgorithm));
            }
            this.jwsAlgorithm = jwsAlgorithm;
        }
    }

    protected String invalidJwsMessage(String jwsAlgorithm) {
        StringBuilder sb = new StringBuilder();
        sb.append("JWS Algorithm: ");
        sb.append(jwsAlgorithm);
        sb.append("not allowed. Valid values: ");
        sb.append(validJwsAlgorithms().stream().reduce((a, b) -> a + "," + b).get());
        return sb.toString();
    }

    protected List<String> validJwsAlgorithms() {
        return Arrays.asList(DEFAULT_JWSALGORITHM,
                "HS384",
                "HS512",
                "RS256",
                "RS384",
                "RS512",
                "ES256",
                "ES384",
                "ES512",
                "PS256",
                "PS384",
                "PS512");
    }

    /**
     * @return The Encryption Method
     */
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
            this.jwsAlgorithm = encryptionMethod;
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
    public String getJweAlgorithm() {
        return jweAlgorithm;
    }

    /**
     * @return The JWS Algorithm
     */
    public void setJweAlgorithm(@Nullable String jweAlgorithm) {
        if ( jweAlgorithm == null ) {
            this.jweAlgorithm = DEFAULT_JWEALGORITHM;
        } else {
            if ( !validJwsAlgorithms().contains(jweAlgorithm) ) {
                throw new IllegalArgumentException(invalidJwsMessage(jweAlgorithm));
            }
            this.jweAlgorithm = jweAlgorithm;
        }
    }

    protected String invalidJweAlgorithms(String jweAlgorithm) {
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
}
