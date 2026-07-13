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
     * Verifies that a cookie name only contains the RFC token characters that Netty's strict cookie
     * encoders allow.
     *
     * @param name The cookie name to verify
     * @throws IllegalArgumentException if the name contains a prohibited character
     */
    static void verifyCookieName(CharSequence name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Cookie name must not be empty");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isValidCookieNameChar(c)) {
                throw new IllegalArgumentException("Cookie name contains an invalid character 0x" + hex(c));
            }
        }
    }

    /**
     * Verifies that a cookie value only contains the RFC 6265 cookie-octets that Netty's strict
     * cookie encoders allow. A balanced pair of wrapping quotes is ignored for validation, matching
     * Netty's strict encoder behavior.
     *
     * @param value The cookie value to verify, treated as empty when {@code null}
     * @throws IllegalArgumentException if the value contains a prohibited character
     */
    static void verifyCookieValue(@Nullable CharSequence value) {
        CharSequence cookieValue = value == null ? "" : value;
        CharSequence unwrappedValue = unwrapCookieValue(cookieValue);
        if (unwrappedValue == null) {
            throw new IllegalArgumentException("Cookie value wrapping quotes are not balanced");
        }
        for (int i = 0; i < unwrappedValue.length(); i++) {
            char c = unwrappedValue.charAt(i);
            if (!isValidCookieValueChar(c)) {
                throw new IllegalArgumentException("Cookie value contains an invalid character 0x" + hex(c));
            }
        }
    }

    /**
     * Verifies that a cookie attribute value does not contain a character that would let it break out
     * of its position in the {@code Set-Cookie} header.
     *
     * @param name The attribute name
     * @param value The attribute value to verify, ignored when {@code null}
     * @throws IllegalArgumentException if the value contains a prohibited character
     */
    static void verifyCookieAttributeValue(String name, @Nullable CharSequence value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isValidCookieAttributeValueChar(c)) {
                throw new IllegalArgumentException(name + " contains a prohibited character 0x" + hex(c));
            }
        }
    }

    private static String hex(char c) {
        return Integer.toHexString(c);
    }

    private static boolean isValidCookieNameChar(char c) {
        return c >= ' ' && c < 0x7f && !isCookieNameSeparator(c);
    }

    private static boolean isCookieNameSeparator(char c) {
        return switch (c) {
            case '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}',
                ' ', '\t' -> true;
            default -> false;
        };
    }

    private static boolean isValidCookieValueChar(char c) {
        return c == 0x21 ||
            (c >= 0x23 && c <= 0x2b) ||
            (c >= 0x2d && c <= 0x3a) ||
            (c >= 0x3c && c <= 0x5b) ||
            (c >= 0x5d && c <= 0x7e);
    }

    private static boolean isValidCookieAttributeValueChar(char c) {
        return c >= ' ' && c < 0x7f && c != ';';
    }

    private static @Nullable CharSequence unwrapCookieValue(CharSequence value) {
        int length = value.length();
        if (length > 0 && value.charAt(0) == '"') {
            if (length >= 2 && value.charAt(length - 1) == '"') {
                return length == 2 ? "" : value.subSequence(1, length - 1);
            }
            return null;
        }
        return value;
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
