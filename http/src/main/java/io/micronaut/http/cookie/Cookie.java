/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.cookie;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;

import java.io.Serializable;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 * An interface representing a Cookie. See .
 * @see <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Cookie extends Comparable<Cookie>, Serializable {

    /**
     * Constant for undefined MaxAge attribute value.
     */
    long UNDEFINED_MAX_AGE = Long.MIN_VALUE;

    /**
     * @see <a href="https://tools.ietf.org/html/rfc6265#section-4.1.1">The Secure Attribute</a>.
     */
    String ATTRIBUTE_SECURE = "Secure";

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.2.6">The HttpOnly Attribute</a>.
     */
    String ATTRIBUTE_HTTP_ONLY = "HttpOnly";

    /**
     * Controls whether a cookie is sent with cross-site requests.
     */
    String ATTRIBUTE_SAME_SITE = "SameSite";

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.2.3">The Domain Attribute</a>
     */
    String ATTRIBUTE_DOMAIN = "Domain";

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.2.4">The Path Attribute</a>.
     */
    String ATTRIBUTE_PATH = "Path";

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.2.1">The Expires Attribute</a>.
     */
    String ATTRIBUTE_EXPIRES = "Expires";

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-5.2.2">The Max-Age Attribute</a>
     */
    String ATTRIBUTE_MAX_AGE = "Max-Age";

    /**
     * @return The name of the cookie
     */
    @NonNull String getName();

    /**
     * @return The value of the cookie
     */
    @NonNull String getValue();

    /**
     * Gets the domain name of this Cookie.
     *
     * <p>Domain names are formatted according to RFC 2109.
     *
     * @return the domain name of this Cookie
     */
    @Nullable String getDomain();

    /**
     * The path of the cookie. The cookie is visible to all paths below the request path on the server.
     *
     * @return The cookie path
     */
    @Nullable String getPath();

    /**
     * Checks to see if this {@link Cookie} can only be accessed via HTTP.
     * If this returns true, the {@link Cookie} cannot be accessed through client side script - But only if the
     * browser supports it.
     * <p>
     * See <a href="https://www.owasp.org/index.php/HTTPOnly">here</a> for reference
     *
     * @return True if this {@link Cookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();

    /**
     * @return True if the cookie is secure
     */
    boolean isSecure();

    /**
     * Gets the maximum age of the cookie in seconds. If the max age has not been explicitly set,
     * then the value returned will be {@link #UNDEFINED_MAX_AGE}, indicating that the Max-Age
     * Attribute should not be written.
     *
     * @return The maximum age of the cookie in seconds
     */
    long getMaxAge();

    /**
     * Checks to see if this {@link Cookie} can be sent along cross-site requests.
     * For more information, please look
     * <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05">here</a>
     * @return The SameSite attribute of the cookie
     */
    default Optional<SameSite> getSameSite() {
        return Optional.empty();
    }

    /**
     * Determines if this {@link Cookie} can be sent along cross-site requests.
     * For more information, please look
     *  <a href="https://tools.ietf.org/html/draft-ietf-httpbis-rfc6265bis-05">here</a>
     * @param sameSite SameSite value
     * @return This cookie
     */
    default @NonNull Cookie sameSite(@Nullable SameSite sameSite) {
        return this;
    }

    /**
     * Sets the max age of the cookie in seconds. When not explicitly set, the max age will default
     * to {@link #UNDEFINED_MAX_AGE} and cause the Max-Age Attribute not to be encoded.
     *
     * @param maxAge The max age
     * @return This cookie
     */
    @NonNull Cookie maxAge(long maxAge);

    /**
     * Sets the value.
     *
     * @param value The value of the cookie
     * @return This cookie
     */
    @NonNull Cookie value(@NonNull String value);

    /**
     * Sets the domain of the cookie.
     *
     * @param domain The domain of the cookie
     * @return This cookie
     */
    @NonNull Cookie domain(@Nullable String domain);

    /**
     * Sets the path of the cookie.
     *
     * @param path The path of the cookie
     * @return This cookie
     */
    @NonNull Cookie path(@Nullable String path);

    /**
     * Sets whether the cookie is secure.
     *
     * @param secure Is the cookie secure
     * @return This cookie
     */
    @NonNull Cookie secure(boolean secure);

    /**
     * Sets this cookie as secure.
     * @return This Cookie
     * @since 4.3.0
     */
    @NonNull
    default Cookie secure() {
        return secure(true);
    }

    /**
     * Sets whether the cookie is HTTP-Only.
     *
     * @param httpOnly Is the cookie HTTP-Only
     * @return This cookie
     */
    @NonNull Cookie httpOnly(boolean httpOnly);

    /**
     * Sets this cookie as HTTP-Only.
     *
     * @return This cookie
     * @since 4.3.0
     */
    @NonNull
    default Cookie httpOnly() {
        return httpOnly(true);
    }

    /**
     * Configure the Cookie with the given configuration.
     * @param configuration The configuration
     * @return The cookie
     */
    default @NonNull Cookie configure(@NonNull CookieConfiguration configuration) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        return configure(configuration, true);
    }

    /**
     * Configure the Cookie with the given configuration.
     * @param configuration The configuration
     * @param isSecure Is the request secure
     * @return The cookie
     */
    default @NonNull Cookie configure(
            @NonNull CookieConfiguration configuration,
            boolean isSecure) {
        ArgumentUtils.requireNonNull("configuration", configuration);
        configuration.getCookiePath().ifPresent(this::path);
        configuration.getCookieDomain().ifPresent(this::domain);
        configuration.getCookieMaxAge().ifPresent(this::maxAge);
        configuration.isCookieHttpOnly().ifPresent(this::httpOnly);
        final Optional<Boolean> secureConfiguration = configuration.isCookieSecure();
        if (secureConfiguration.isPresent()) {
            secure(secureConfiguration.get());
        } else {
            secure(isSecure);
        }
        configuration.getCookieSameSite().ifPresent(this::sameSite);
        return this;
    }

    /**
     * Sets the max age of the cookie.
     *
     * @param maxAge The max age
     * @return This cookie
     */
    default @NonNull Cookie maxAge(@NonNull TemporalAmount maxAge) {
        ArgumentUtils.requireNonNull("maxAge", maxAge);
        return maxAge(maxAge.get(ChronoUnit.SECONDS));
    }

    /**
     * Construct a new Cookie for the given name and value.
     *
     * @param name  The name
     * @param value The value
     * @return The Cookie
     */
    static @NonNull Cookie of(@NonNull String name, @NonNull String value) {
        CookieFactory instance = CookieFactory.INSTANCE;
        if (instance != null) {
            return instance.create(name, value);
        }
        throw new UnsupportedOperationException("No CookeFactory implementation found. Server implementation does not support cookies.");
    }
}
