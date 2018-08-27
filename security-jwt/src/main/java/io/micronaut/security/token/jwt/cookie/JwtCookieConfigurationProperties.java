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
import io.micronaut.core.util.StringUtils;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(JwtCookieConfigurationProperties.PREFIX)
public class JwtCookieConfigurationProperties implements JwtCookieConfiguration {
    public static final String PREFIX = JwtConfigurationProperties.PREFIX + ".cookie";

    private boolean enabled = false;
    private String logoutTargetUrl = "/";
    private String cookieName = "JWT";
    private String loginSuccessTargetUrl = "/";
    private String loginFailureTargetUrl = "/";

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

    /**
     * Sets whether JWT cookie based security is enabled.
     *
     * @param enabled True if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the logout target URL.
     * @param logoutTargetUrl The URL
     */
    public void setLogoutTargetUrl(String logoutTargetUrl) {
        if (StringUtils.isNotEmpty(logoutTargetUrl)) {
            this.logoutTargetUrl = logoutTargetUrl;
        }
    }

    /**
     * Sets the cookie name to use.
     * @param cookieName The cookie name
     */
    public void setCookieName(String cookieName) {
        if (StringUtils.isNotEmpty(cookieName)) {
            this.cookieName = cookieName;
        }
    }

    /**
     * Sets the login success target URL.
     * @param loginSuccessTargetUrl The URL
     */
    public void setLoginSuccessTargetUrl(String loginSuccessTargetUrl) {
        if (StringUtils.isNotEmpty(loginSuccessTargetUrl)) {
            this.loginSuccessTargetUrl = loginSuccessTargetUrl;
        }
    }

    /**
     * Sets the login failure target URL.
     * @param loginFailureTargetUrl The URL
     */
    public void setLoginFailureTargetUrl(String loginFailureTargetUrl) {
        if (StringUtils.isNotEmpty(loginFailureTargetUrl)) {
            this.loginFailureTargetUrl = loginFailureTargetUrl;
        }
    }
}
