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
package io.micronaut.security.token.jwt.cookie;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;
import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(JwtCookieConfigurationProperties.PREFIX)
public class JwtCookieConfigurationProperties implements JwtCookieConfiguration {
    public static final String PREFIX = JwtConfigurationProperties.PREFIX + ".cookie";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = false;

    /**
     * The default secure value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_SECURE = true;

    /**
     * The default http only value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_HTTPONLY = true;

    /**
     * The default cookie name.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_COOKIENAME = "JWT";

    /**
     * Default Cookie Path.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_COOKIEPATH = "/";

    /**
     * The default logout target URL.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_LOGOUTTARGETURL = "/";

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

    @Nullable
    private String cookieDomain;

    @Nullable
    private String cookiePath = DEFAULT_COOKIEPATH;

    @Nullable
    private Boolean cookieHttpOnly = DEFAULT_HTTPONLY;

    @Nullable
    private Boolean cookieSecure = DEFAULT_SECURE;

    @Nullable
    private TemporalAmount cookieMaxAge;

    private boolean enabled = DEFAULT_ENABLED;
    private String logoutTargetUrl = DEFAULT_LOGOUTTARGETURL;
    private String cookieName = DEFAULT_COOKIENAME;
    private String loginSuccessTargetUrl = DEFAULT_LOGINSUCCESSTARGETURL;
    private String loginFailureTargetUrl = DEFAULT_LOGINFAILURETARGETURL;

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
     * Sets the logout target URL. Default value ({@value #DEFAULT_LOGOUTTARGETURL}).
     * @param logoutTargetUrl The URL
     */
    public void setLogoutTargetUrl(String logoutTargetUrl) {
        if (StringUtils.isNotEmpty(logoutTargetUrl)) {
            this.logoutTargetUrl = logoutTargetUrl;
        }
    }

    /**
     * Sets the login success target URL. Default value ({@value #DEFAULT_LOGINSUCCESSTARGETURL}).
     * @param loginSuccessTargetUrl The URL
     */
    public void setLoginSuccessTargetUrl(String loginSuccessTargetUrl) {
        if (StringUtils.isNotEmpty(loginSuccessTargetUrl)) {
            this.loginSuccessTargetUrl = loginSuccessTargetUrl;
        }
    }

    /**
     * Sets the login failure target URL. Default value ({@value #DEFAULT_LOGINFAILURETARGETURL}).
     * @param loginFailureTargetUrl The URL
     */
    public void setLoginFailureTargetUrl(String loginFailureTargetUrl) {
        if (StringUtils.isNotEmpty(loginFailureTargetUrl)) {
            this.loginFailureTargetUrl = loginFailureTargetUrl;
        }
    }

    /**
     * Sets whether JWT cookie based security is enabled. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled True if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Cookie Name. Default value ({@value #DEFAULT_COOKIENAME}).
     * @param cookieName Cookie name
     */
    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    /**
     *
     * @return a name for the cookie
     */
    @Nonnull
    @Override
    public String getCookieName() {
        return this.cookieName;
    }

    /**
     *
     * @return the domain name of this Cookie
     */
    @Override
    public Optional<String> getCookieDomain() {
        return Optional.ofNullable(cookieDomain);
    }

    /**
     *
     * @return The path of the cookie.
     */
    @Nullable
    @Override
    public Optional<String> getCookiePath() {
        return Optional.ofNullable(cookiePath);
    }

    /**
     * @return Whether the Cookie can only be accessed via HTTP.
     */
    @Override
    public Optional<Boolean> isCookieHttpOnly() {
        return Optional.ofNullable(cookieHttpOnly);
    }

    /**
     *
     * @return True if the cookie is secure
     */
    @Override
    public Optional<Boolean>  isCookieSecure() {
        return Optional.ofNullable(cookieSecure);
    }

    /**
     * @return The max age to use for the cookie
     */
    @Override
    public Optional<TemporalAmount> getCookieMaxAge() {
        return Optional.ofNullable(cookieMaxAge);
    }

    /**
     * Sets the domain name of this Cookie. Default value ({@value #DEFAULT_COOKIENAME}).
     * @param cookieDomain the domain name of this Cookie
     */
    public void setCookieDomain(@Nullable String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    /**
     * Sets the path of the cookie. Default value ({@value #DEFAULT_COOKIEPATH}.
     * @param cookiePath The path of the cookie.
     */
    public void setCookiePath(@Nullable String cookiePath) {
        this.cookiePath = cookiePath;
    }

    /**
     * Whether the Cookie can only be accessed via HTTP. Default value ({@value #DEFAULT_HTTPONLY}.
     * @param cookieHttpOnly Whether the Cookie can only be accessed via HTTP
     */
    public void setCookieHttpOnly(Boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    /**
     * Sets whether the cookie is secured. Default value ({@value #DEFAULT_SECURE}.
     * @param cookieSecure True if the cookie is secure
     */
    public void setCookieSecure(Boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * Sets the maximum age of the cookie.
     * @param cookieMaxAge The maximum age of the cookie
     */
    public void setCookieMaxAge(TemporalAmount cookieMaxAge) {
        this.cookieMaxAge = cookieMaxAge;
    }
}
