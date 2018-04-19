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

package io.micronaut.security.token.reader;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.token.generator.TokenConfigurationProperties;

/**
 * Default implementation of {@link CookieTokenReaderConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(CookieTokenReaderConfigurationProperties.PREFIX)
public class CookieTokenReaderConfigurationProperties implements CookieTokenReaderConfiguration {

    public static final String PREFIX = TokenConfigurationProperties.PREFIX + ".cookie";

    protected boolean enabled = false;
    protected String cookieName = "JWT";

    /**
     * enabled getter.
     * @return a boolean flag indicating wether the feature is enabled or not
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * cookieName getter.
     * @return a String with the Cookie Name
     */
    @Override
    public String getCookieName() {
        return cookieName;
    }
}
