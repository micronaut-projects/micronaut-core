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

package io.micronaut.http.cookie;

import java.io.Serializable;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

/**
 * An interface representing a Cookie. See https://tools.ietf.org/html/rfc6265.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Cookie extends Comparable<Cookie>, Serializable {

    /**
     * @return The name of the cookie
     */
    String getName();

    /**
     * @return The value of the cookie
     */
    String getValue();

    /**
     * Gets the domain name of this Cookie.
     *
     * <p>Domain names are formatted according to RFC 2109.
     *
     * @return the domain name of this Cookie
     */
    String getDomain();

    /**
     * The path of the cookie. The cookie is visible to all paths below the request path on the server.
     *
     * @return The cookie path
     */
    String getPath();

    /**
     * Checks to see if this {@link Cookie} can only be accessed via HTTP.
     * If this returns true, the {@link Cookie} cannot be accessed through client side script - But only if the
     * browser supports it.
     * <p>
     * See <a href="http://www.owasp.org/index.php/HTTPOnly">here</a> for reference
     *
     * @return True if this {@link Cookie} is HTTP-only or false if it isn't
     */
    boolean isHttpOnly();

    /**
     * @return True if the cookie is secure
     */
    boolean isSecure();

    /**
     * @return The maximum age of the cookie in seconds
     */
    long getMaxAge();

    /**
     * Sets the max age of the cookie in seconds.
     *
     * @param maxAge The max age
     * @return This cookie
     */
    Cookie maxAge(long maxAge);

    /**
     * Sets the value.
     *
     * @param value The value of the cookie
     * @return This cookie
     */
    Cookie value(String value);

    /**
     * Sets the domain of the cookie.
     *
     * @param domain The domain of the cookie
     * @return This cookie
     */
    Cookie domain(String domain);

    /**
     * Sets the path of the cookie.
     *
     * @param path The path of the cookie
     * @return This cookie
     */
    Cookie path(String path);

    /**
     * Sets whether the cookie is secure.
     *
     * @param secure Is the cookie secure
     * @return This cookie
     */
    Cookie secure(boolean secure);

    /**
     * Sets whether the cookie is HTTP-Only.
     *
     * @param httpOnly Is the cookie HTTP-Only
     * @return This cookie
     */
    Cookie httpOnly(boolean httpOnly);

    /**
     * Sets the max age of the cookie in seconds.
     *
     * @param maxAge The max age
     * @return This cookie
     */
    default Cookie maxAge(TemporalAmount maxAge) {
        return maxAge(maxAge.get(ChronoUnit.SECONDS));
    }

    /**
     * Construct a new Cookie for the given name and value.
     *
     * @param name  The name
     * @param value The value
     * @return The Cookie
     */
    static Cookie of(String name, String value) {
        CookieFactory instance = CookieFactory.INSTANCE;
        if (instance != null) {
            return instance.create(name, value);
        }
        throw new UnsupportedOperationException("No CookeFactory implementation found. Server implementation does not support cookies.");
    }
}
