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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

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
     * @param cookie        Cookie
     * @param cookieEncoded Encoded cookie
     */
    public static void verifyCookieSize(Cookie cookie,
                                        String cookieEncoded) {
        verifyCookieSize(cookie, cookieEncoded, COOKIE_BYTE_LIMIT);
    }

    /**
     * @param cookie          Cookie
     * @param cookieEncoded   Encoded cookie
     * @param cookieByteLimit Cookie byte Limit
     */
    public static void verifyCookieSize(Cookie cookie,
                                        String cookieEncoded,
                                        Integer cookieByteLimit) {
        int byteCount = StringUtils.utf8Bytes(cookieEncoded);
        if (byteCount > cookieByteLimit) {
            throw new CookieSizeExceededException(cookie.getName(), cookieByteLimit, byteCount);
        }
    }

    /**
     * Verifies that a cookie component (name, value, path, domain, ...) does not contain a character
     * that would let it break out of its position in the {@code Set-Cookie} or {@code Cookie} header.
     * Control characters enable HTTP response splitting / header injection
     * (<a href="https://cwe.mitre.org/data/definitions/113.html">CWE-113</a>) and {@code ;} enables
     * cookie attribute injection. The Netty backed encoders reject the same characters; the pure-Java
     * encoders rely on this method to stay consistent.
     *
     * @param component The cookie component to verify, ignored when {@code null}
     * @throws IllegalArgumentException if the component contains a prohibited character
     */
    static void verifyCookieComponent(@Nullable CharSequence component) {
        if (component == null) {
            return;
        }
        for (int i = 0; i < component.length(); i++) {
            char c = component.charAt(i);
            if (c < ' ' || c == 0x7f || c == ';') {
                throw new IllegalArgumentException("Cookie contains a prohibited character 0x"
                    + Integer.toHexString(c) + " at index " + i);
            }
        }
    }

    /**
     * Sets the HTTP Header Set-Cookie with the supplied cookie encoded.
     *
     * @param headers         HTTP Headers
     * @param cookie          Cookie
     * @param cookieByteLimit Cookie byte Limit
     */
    public static void setCookieHeader(MutableHttpHeaders headers,
                                       Cookie cookie,
                                       Integer cookieByteLimit) {
        ServerCookieEncoder.INSTANCE.encode(cookie)
            .forEach(cookieEncoded -> {
                CookieUtils.verifyCookieSize(cookie, cookieEncoded, cookieByteLimit);
                headers.add(SET_COOKIE, cookieEncoded);
            });
    }

    /**
     * Sets the HTTP Header Set-Cookie with the supplied cookie encoded.
     *
     * @param headers HTTP Headers
     * @param cookie  Cookie
     */
    public static void setCookieHeader(MutableHttpHeaders headers, Cookie cookie) {
        setCookieHeader(headers, cookie, COOKIE_BYTE_LIMIT);
    }
}
