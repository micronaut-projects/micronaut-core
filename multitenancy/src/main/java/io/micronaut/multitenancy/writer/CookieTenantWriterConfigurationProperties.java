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
package io.micronaut.multitenancy.writer;

import io.micronaut.context.annotation.ConfigurationProperties;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 *  {@link io.micronaut.context.annotation.ConfigurationProperties} implementation of {@link io.micronaut.multitenancy.writer.CookieTenantWriterConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(CookieTenantWriterConfigurationProperties.PREFIX)
public class CookieTenantWriterConfigurationProperties implements CookieTenantWriterConfiguration {
    public static final String PREFIX = TenantWriter.PREFIX + ".cookie";
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
    public static final String DEFAULT_COOKIENAME = "tenantId";

    /**
     * Default Cookie Path.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_COOKIEPATH = "/";

    @NonNull
    private String cookiename = DEFAULT_COOKIENAME;

    @Nullable
    private String cookieDomain;

    @Nullable
    private String cookiePath = DEFAULT_COOKIEPATH;

    private Boolean cookieHttpOnly = DEFAULT_HTTPONLY;
    private Boolean cookieSecure = DEFAULT_SECURE;
    private Duration cookieMaxAge;
    private boolean enabled = DEFAULT_ENABLED;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.multitenancy.writer.CookieTenantWriter}. Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled enabled flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Cookie Name. Default value ({@value #DEFAULT_COOKIENAME}).
     * @param cookiename Cookie name
     */
    public void setCookiename(String cookiename) {
        this.cookiename = cookiename;
    }

    /**
     *
     * @return an HTTP Header name. e.g. Authorization
     */
    @NonNull
    @Override
    public String getCookiename() {
        return this.cookiename;
    }

    @NonNull
    @Override
    public String getCookieName() {
        return this.cookiename;
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
    public Optional<Boolean> isCookieSecure() {
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
     * @param cookieHttpOnly Whether the Cookie can only be accessed via HTTP.
     */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    /**
     * Sets whether the cookie is secured. Default value ({@value #DEFAULT_SECURE}.
     * @param cookieSecure True if the cookie is secure
     */
    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    /**
     * Sets the maximum age of the cookie.
     * @param cookieMaxAge The maximum age of the cookie
     */
    public void setCookieMaxAge(Duration cookieMaxAge) {
        this.cookieMaxAge = cookieMaxAge;
    }
}
