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

package io.micronaut.security.token.jwt.signature;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class JwtSignatureConfigurationFactory {

    /**
     * Instantiates a {@link RSASignatureConfiguration}, {@link ECSignatureConfiguration} or {@link SecretSignatureConfiguration} depending on {@link JwtSignatureConfiguration}.
     * @param jwtSignatureConfiguration Instance of {@link JwtSignatureConfiguration}
     * @return An object conforming to {@link SignatureConfiguration}
     */
    public static SignatureConfiguration create(JwtSignatureConfiguration jwtSignatureConfiguration) {

        switch (jwtSignatureConfiguration.getType()) {
            case RSA:
                return new RSASignatureConfiguration(jwtSignatureConfiguration);

            case EC:
                return new ECSignatureConfiguration(jwtSignatureConfiguration);

            default:
            case SECRET:
                return new SecretSignatureConfiguration(jwtSignatureConfiguration);
        }
    }
}
