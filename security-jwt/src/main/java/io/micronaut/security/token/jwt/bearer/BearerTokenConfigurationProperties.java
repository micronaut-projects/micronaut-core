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

package io.micronaut.security.token.jwt.bearer;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

/**
 * Default implementation of {@link BearerTokenConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(BearerTokenConfigurationProperties.PREFIX)
public class BearerTokenConfigurationProperties implements BearerTokenConfiguration {

    public static final String PREFIX = JwtConfigurationProperties.PREFIX + ".bearer";

    protected boolean enabled = true;
    protected String headerName = "Authorization";
    protected String prefix = "Bearer";

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public String getHeaderName() {
        return headerName;
    }
}
