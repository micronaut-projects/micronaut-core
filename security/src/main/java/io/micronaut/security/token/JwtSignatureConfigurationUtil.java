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
package io.micronaut.security.token;

import com.nimbusds.jose.JWSAlgorithm;
import org.pac4j.jwt.config.signature.SecretSignatureConfiguration;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class JwtSignatureConfigurationUtil {

    public static SecretSignatureConfiguration createSignatureConfiguration(SignatureConfiguration config) {
        final JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(config.getJwsAlgorithm());
        final String secret = config.getSecret();
        final SecretSignatureConfiguration signatureConfiguration = new SecretSignatureConfiguration(secret, jwsAlgorithm);
        return signatureConfiguration;
    }
}
