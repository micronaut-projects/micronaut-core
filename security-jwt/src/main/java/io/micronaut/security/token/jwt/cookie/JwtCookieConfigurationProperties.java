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

package io.micronaut.security.token.jwt.cookie;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(JwtCookieConfigurationProperties.PREFIX)
public class JwtCookieConfigurationProperties implements JwtCookieConfiguration {
    public static final String PREFIX = JwtConfigurationProperties.PREFIX + ".cookie";

    protected boolean enabled = false;
    protected String logoutTargetUrl = "/";
    protected String cookieName = "JWT";
    protected String loginSuccessTargetUrl = "/";
    protected String loginFailureTargetUrl = "/";

    /**
     *
     * @return a boolean flag indicating whether the JwtCookieTokenReader should be enabled or not
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLogoutTargetUrl() {
        return this.logoutTargetUrl;
    }

    @Override
    public String getLoginSuccessTargetUrl() {
        return loginSuccessTargetUrl;
    }

    @Override
    public String getLoginFailureTargetUrl() {
        return loginFailureTargetUrl;
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
