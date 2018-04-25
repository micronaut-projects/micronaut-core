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

package io.micronaut.security.jwt.signature;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.core.util.Toggleable;
import io.micronaut.security.jwt.config.CryptoAlgorithm;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface JwtSignatureConfiguration extends Toggleable {

    /**
     * Boolean flag to indicate if you want to enable JWT signature configuration.
     * @return true or false
     */
    boolean isEnabled();

    /**
     * Designates signature type.
     * @return Type signature SECRET, RSA, EC
     */
    CryptoAlgorithm getType();

    /**
     * @return The JWS Algorithm
     */
    JWSAlgorithm getJwsAlgorithm();

    /**
     *
     * @return Secret's length must be at least 256 bits. it is used to sign JWT.
     */
    String getSecret();

    /**
     * @return The path to the PEM file
     */
    String getPemPath();
}
