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

package io.micronaut.security.token.jwt.signature.secret;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@EachProperty(JwtConfigurationProperties.PREFIX + ".signatures.secret")
public class SecretSignatureConfiguration {
    private JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    private String secret;
    private final String name;

    /**
     *
     * @param name Bean's qualifier name
     */
    public SecretSignatureConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The JWS Algorithm
     */
    public JWSAlgorithm getJwsAlgorithm() {
        return jwsAlgorithm;
    }

    /**
     * jwsAlgorithm setter.
     * @param jwsAlgorithm JWS Algorithm
     */
    public void setJwsAlgorithm(JWSAlgorithm jwsAlgorithm) {
        this.jwsAlgorithm = jwsAlgorithm;
    }

    /**
     *
     * @return Secret's length must be at least 256 bits. it is used to sign JWT.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * secret setter.
     * @param secret Signature Secret
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     *
     * @return Bean's qualifier name
     */
    public String getName() {
        return name;
    }

}
