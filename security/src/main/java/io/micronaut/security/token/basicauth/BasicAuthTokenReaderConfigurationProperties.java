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

package io.micronaut.security.token.basicauth;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.security.token.config.TokenConfigurationProperties;

/**
 * Default implementation of {@link BasicAuthTokenReaderConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = TokenConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@ConfigurationProperties(BasicAuthTokenReaderConfigurationProperties.PREFIX)
public class BasicAuthTokenReaderConfigurationProperties implements BasicAuthTokenReaderConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".basicAuth";

    private boolean enabled = true;
    private String headerName = HttpHeaders.AUTHORIZATION;
    private String prefix = "Basic";

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

    /**
     * Set whether to enable basic auth.
     *
     * @param enabled True if enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the header name to use. Defaults to {@link io.micronaut.http.HttpHeaders#AUTHORIZATION}.
     *
     * @param headerName The header name to use
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /**
     * Sets the prefix to use for the auth token. Defaults to "Basic".
     * @param prefix The prefix to use
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
