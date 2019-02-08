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
package io.micronaut.security.token.jwt.signature;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;

/**
 * Signature configuration.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface SignatureConfiguration {

    /**
     *
     * @return A message indicating the supported algorithms.
     */
    String supportedAlgorithmsMessage();

    /**
     * Whether this signature configuration supports this algorithm.
     *
     * @param algorithm the signature algorithm
     * @return whether this signature configuration supports this algorithm
     */
    boolean supports(JWSAlgorithm algorithm);

    /**
     * Verify a signed JWT.
     *
     * @param jwt the signed JWT
     * @return whether the signed JWT is verified
     * @throws JOSEException exception when verifying the JWT
     */
    boolean verify(SignedJWT jwt) throws JOSEException;
}

