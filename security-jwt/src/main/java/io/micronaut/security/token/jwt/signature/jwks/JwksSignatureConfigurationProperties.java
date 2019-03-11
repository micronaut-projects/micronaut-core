/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.security.token.jwt.signature.jwks;

import com.nimbusds.jose.jwk.KeyType;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

import javax.validation.constraints.NotNull;

/**
 * JSON Web Key Set (JWKS) Signature Configuration properties holder.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@EachProperty(JwksSignatureConfigurationProperties.PREFIX)
public class JwksSignatureConfigurationProperties implements JwksSignatureConfiguration {

    public static final String PREFIX = JwtConfigurationProperties.PREFIX + ".signatures.jwks";

    /**
     * The default key type.
     */
    @SuppressWarnings("WeakerAccess")
    public static final KeyType DEFAULT_KEYTYPE = KeyType.RSA;

    private String url;

    private KeyType keyType = DEFAULT_KEYTYPE;

    /**
     *
     * @return URL to the remote JSON Web Key Set.
     */
    @Override
    @NotNull
    public String getUrl() {
        return url;
    }

    /**
     * Remote JSON Web Key set url. e.g. https://.../.well-known/jwks.json
     * @param url Remote JSON Web Key set url.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     *
     * @return Representation the KeyType for this JWKS signature configuration. KeyType is the kty parameter in a JSON Web Key (JWK).
     */
    @Override
    @NotNull
    public KeyType getKeyType() {
        return keyType;
    }

    /**
     * Representation of the kty parameter in a JSON Web Key (JWK). Default value (RSA).
     * @param keyType Representation of the kty parameter in a JSON Web Key (JWK).
     */
    public void setKeyType(KeyType keyType) {
        this.keyType = keyType;
    }
}
