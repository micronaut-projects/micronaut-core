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

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfigurationProperties;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(JwtGeneratorSignatureConfigurationProperties.PREFIX)
public class JwtGeneratorSignatureConfigurationProperties {
    public static final String PREFIX = JwtGeneratorConfigurationProperties.PREFIX + ".signature";

    protected JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    protected String secret;

    /**
     *
     * @return {@link JWSAlgorithm}
     */
    public JWSAlgorithm getJwsAlgorithm() {
        return jwsAlgorithm;
    }

    /**
     *
     * @return Secret's length must be at least 256 bits. it is used to sign JWT.
     */
    public String getSecret() {
        return secret;
    }
}
