/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpHeaders;

/**
 * Utils class to work with cookies.
 */
@Internal
public final class CookieUtils {

    /**
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-6.1">Cookie Limits</a>
     */
    private static final int COOKIE_BYTE_LIMIT = 4096;
    private static final String SET_COOKIE = "Set-Cookie";

    private CookieUtils() {
    }

    /**
     *
     * @param cookie Cookie
     * @param cookieEncoded Encoded cookie
     */
    public static void verifyCookieSize(@NonNull Cookie cookie,
                                        @NonNull String cookieEncoded) {
        verifyCookieSize(cookie, cookieEncoded, COOKIE_BYTE_LIMIT);
    }

    /**
     * @param cookie Cookie
     * @param cookieEncoded Encoded cookie
     * @param cookieByteLimit Cookie byte Limit
     */
    public static void verifyCookieSize(@NonNull Cookie cookie,
                                        @NonNull String cookieEncoded,
                                        @NonNull Integer cookieByteLimit) {
            int byteCount = StringUtils.utf8Bytes(cookieEncoded);
            if (byteCount > cookieByteLimit) {
                throw new CookieSizeExceededException(cookie.getName(), cookieByteLimit, byteCount);
            }
    }

    /**
     * Sets the HTTP Header Set-Cookie with the supplied cookie encoded.
     * @param headers HTTP Headers
     * @param cookie Cookie
     * @param cookieByteLimit Cookie byte Limit
     */
    public static void setCookieHeader(@NonNull MutableHttpHeaders headers,
                                       @NonNull Cookie cookie,
                                       @NonNull Integer cookieByteLimit) {
        ServerCookieEncoder.INSTANCE.encode(cookie)
            .forEach(cookieEncoded -> {
                CookieUtils.verifyCookieSize(cookie, cookieEncoded, cookieByteLimit);
                headers.add(SET_COOKIE, cookieEncoded);
            });
    }

    /**
     * Sets the HTTP Header Set-Cookie with the supplied cookie encoded.
     * @param headers HTTP Headers
     * @param cookie Cookie
     */
    public static void setCookieHeader(@NonNull MutableHttpHeaders headers, @NonNull Cookie cookie) {
        setCookieHeader(headers, cookie, COOKIE_BYTE_LIMIT);
    }
}
