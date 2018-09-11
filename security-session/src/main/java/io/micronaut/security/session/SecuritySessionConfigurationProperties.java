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

package io.micronaut.security.session;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;
import io.micronaut.security.config.SecurityConfigurationProperties;

/**
 * Implementation of {@link SecuritySessionConfiguration}. Session-based Authentication configuration properties.
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(SecuritySessionConfigurationProperties.PREFIX)
public class SecuritySessionConfigurationProperties implements SecuritySessionConfiguration {
    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".session";

    private String loginSuccessTargetUrl = "/";
    private String loginFailureTargetUrl = "/";
    private String logoutTargetUrl = "/";
    private String unauthorizedTargetUrl;
    private String forbiddenTargetUrl;
    private boolean enabled = false;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getLoginSuccessTargetUrl() {
        return this.loginSuccessTargetUrl;
    }

    @Override
    public String getLogoutTargetUrl() {
        return this.logoutTargetUrl;
    }

    @Override
    public String getLoginFailureTargetUrl() {
        return this.loginFailureTargetUrl;
    }

    @Override
    public String getUnauthorizedTargetUrl()  {
        return unauthorizedTargetUrl;
    }

    @Override
    public String getForbiddenTargetUrl()  {
        return forbiddenTargetUrl;
    }

    /**
     * Sets the login success target URL.
     *
     * @param loginSuccessTargetUrl The URL
     */
    public void setLoginSuccessTargetUrl(String loginSuccessTargetUrl) {
        if (StringUtils.isNotEmpty(loginSuccessTargetUrl)) {
            this.loginSuccessTargetUrl = loginSuccessTargetUrl;
        }
    }

    /**
     * Sets the login failure target URL.
     *
     * @param loginFailureTargetUrl The URL
     */
    public void setLoginFailureTargetUrl(String loginFailureTargetUrl) {
        if (StringUtils.isNotEmpty(loginFailureTargetUrl)) {
            this.loginFailureTargetUrl = loginFailureTargetUrl;
        }
    }

    /**
     * Sets the logout target URL.
     *
     * @param logoutTargetUrl The URL
     */
    public void setLogoutTargetUrl(String logoutTargetUrl) {
        if (StringUtils.isNotEmpty(logoutTargetUrl)) {
            this.logoutTargetUrl = logoutTargetUrl;
        }
    }

    /**
     * Sets the unauthorized target URL.
     *
     * @param unauthorizedTargetUrl The URL
     */
    public void setUnauthorizedTargetUrl(String unauthorizedTargetUrl) {
        if (StringUtils.isNotEmpty(unauthorizedTargetUrl)) {
            this.unauthorizedTargetUrl = unauthorizedTargetUrl;
        }
    }

    /**
     * Sets the forbidden target URL.
     *
      * @param forbiddenTargetUrl The URL
     */
    public void setForbiddenTargetUrl(String forbiddenTargetUrl) {
        if (StringUtils.isNotEmpty(forbiddenTargetUrl)) {
            this.forbiddenTargetUrl = forbiddenTargetUrl;
        }
    }

    /**
     * Sets whether the session config is enabled.
     *
     * @param enabled True if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
