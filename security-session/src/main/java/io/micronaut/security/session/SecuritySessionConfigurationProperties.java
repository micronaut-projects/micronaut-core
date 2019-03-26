/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.http.HttpRequest;
import io.micronaut.security.config.SecurityConfigurationProperties;
import io.micronaut.security.handlers.ForbiddenRejectionUriProvider;
import io.micronaut.security.handlers.UnauthorizedRejectionUriProvider;

import java.util.Optional;

/**
 * Implementation of {@link SecuritySessionConfiguration}. Session-based Authentication configuration properties.
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(SecuritySessionConfigurationProperties.PREFIX)
public class SecuritySessionConfigurationProperties implements SecuritySessionConfiguration,
        UnauthorizedRejectionUriProvider,
        ForbiddenRejectionUriProvider {
    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".session";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default login success target URL.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_LOGINSUCCESSTARGETURL = "/";

    /**
     * The default login failure target URL.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_LOGINFAILURETARGETURL = "/";

    /**
     * The default login target URL.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_LOGOUTTARGETURL = "/";

    /**
     * The default value to disable rejection handler.
     */
    @Deprecated
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_LEGACYREJECTIONHANDLER = true;

    private String loginSuccessTargetUrl = DEFAULT_LOGINSUCCESSTARGETURL;
    private String loginFailureTargetUrl = DEFAULT_LOGINFAILURETARGETURL;
    private String logoutTargetUrl = DEFAULT_LOGOUTTARGETURL;
    private String unauthorizedTargetUrl;
    private String forbiddenTargetUrl;
    private boolean enabled = DEFAULT_ENABLED;

    @Deprecated
    private boolean legacyRejectionHandler = DEFAULT_LEGACYREJECTIONHANDLER;

    @Override
    public boolean isLegacyRejectionHandler() {
        return legacyRejectionHandler;
    }

    /**
     * Decides whether the deprecated {@link SessionSecurityFilterOrderProvider} is loaded, instead of the new {@link io.micronaut.security.handlers.RedirectRejectionHandler}. Defaults to ({@value #DEFAULT_LEGACYREJECTIONHANDLER}).
     * @param legacyRejectionHandler whether the deprecated {@link SessionSecurityFilterOrderProvider} is loaded
     */
    public void setLegacyRejectionHandler(boolean legacyRejectionHandler) {
        this.legacyRejectionHandler = legacyRejectionHandler;
    }
    
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
     * Sets the login success target URL. Default value ({@value #DEFAULT_LOGINSUCCESSTARGETURL}).
     *
     * @param loginSuccessTargetUrl The URL
     */
    public void setLoginSuccessTargetUrl(String loginSuccessTargetUrl) {
        if (StringUtils.isNotEmpty(loginSuccessTargetUrl)) {
            this.loginSuccessTargetUrl = loginSuccessTargetUrl;
        }
    }

    /**
     * Sets the login failure target URL. Default value ({@value #DEFAULT_LOGINFAILURETARGETURL}).
     *
     * @param loginFailureTargetUrl The URL
     */
    public void setLoginFailureTargetUrl(String loginFailureTargetUrl) {
        if (StringUtils.isNotEmpty(loginFailureTargetUrl)) {
            this.loginFailureTargetUrl = loginFailureTargetUrl;
        }
    }

    /**
     * Sets the logout target URL. Default value ({@value #DEFAULT_LOGOUTTARGETURL}).
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
     * Sets whether the session config is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return A uri to redirect to when a user tries to access a secured resource without authentication.
     */
    public Optional<String> getUnauthorizedRedirectUri() {
        return Optional.ofNullable(unauthorizedTargetUrl);
    }

    @Override
    public Optional<String> getUnauthorizedRedirectUri(HttpRequest<?> request) {
        return getUnauthorizedRedirectUri();
    }

    /**
     * @return A uri to redirect to when an authenticated user tries to access a resource for which he does not have the required authorization level.
     */
    public Optional<String> getForbiddenRedirectUri() {
        return Optional.ofNullable(forbiddenTargetUrl);
    }

    @Override
    public Optional<String> getForbiddenRedirectUri(HttpRequest<?> request) {
        return getForbiddenRedirectUri();
    }
}
