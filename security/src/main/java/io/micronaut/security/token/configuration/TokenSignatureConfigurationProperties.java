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

package io.micronaut.security.token.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(TokenSignatureConfigurationProperties.PREFIX)
public class TokenSignatureConfigurationProperties implements TokenSignatureConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".signature";

    protected JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
    protected SignatureConfiguration type = SignatureConfiguration.SECRET;
    protected boolean enabled = true;

    @NotNull
    @NotBlank
    protected String secret;

    @Override
    public SignatureConfiguration getType() {
        return type;
    }

    @Override
    public JWSAlgorithm getJwsAlgorithm() {
        return jwsAlgorithm;
    }

    @Override
    public String getSecret() {
        return secret;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
