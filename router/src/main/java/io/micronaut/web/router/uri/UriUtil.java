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
package io.micronaut.web.router.uri;

import io.micronaut.core.annotation.NonNull;

import java.net.URI;

/**
 * Utilities for converting URI formats.
 *
 * @author Jonas Konrad
 * @since 4.9.0
 */
public final class UriUtil {
    private UriUtil() {
    }

    /**
     * Transform a path+query as specified by the whatwg url spec into a path+query that is allowed
     * by RFC 3986. Whatwg permits certain characters (e.g. '|') and invalid percent escape
     * sequences that RFC 3986 (or {@link URI}) does not allow. This method will percent-encode
     * those cases, so that any URI sent by a browser can be transformed to {@link URI}.
     *
     * @param path The whatwg path+query
     * @return A valid RFC 3986 {@code relative-ref}
     */
    public static String toValidPath(String path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length();) {
            int cp = path.codePointAt(i);
            if (cp == '%') {
                boolean validEscape;
                if (i + 2 >= path.length()) {
                    validEscape = false;
                } else {
                    char c1 = path.charAt(i + 1);
                    char c2 = path.charAt(i + 2);
                    validEscape = isAsciiHexDigit(c1) && isAsciiHexDigit(c2);
                }
                if (validEscape) {
                    sb.appendCodePoint(cp);
                } else {
                    PercentEncoder.appendEncodedByte(sb, (byte) '%');
                }
            } else {
                if (cp == '/' && sb.length() == 1 && sb.charAt(0) == '/') {
                    // prevent '//' at start of url
                } else {
                    PercentEncoder.RFC3986_QUERY_CHAR.encodeUtf8(sb, cp);
                }
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /**
     * Check whether the given HTTP request target is a valid RFC 3986 relative URI (path + query)
     * that will be parsed without complaint by {@link URI}. If this is true, we can skip the
     * expensive parsing until necessary.
     *
     * @param requestTarget The HTTP request line
     * @return {@code true} iff this is a valid relative URI
     */
    public static boolean isValidPath(@NonNull String requestTarget) {
        if (requestTarget.isEmpty() || requestTarget.charAt(0) != '/') {
            return false;
        }
        for (int i = 0; i < requestTarget.length(); i++) {
            char c = requestTarget.charAt(i);
            if (c == '%' || c > 0x7f || !PercentEncoder.RFC3986_QUERY_CHAR.keep((byte) c)) {
                return false;
            }
            if (c == '/' && i < requestTarget.length() - 1) {
                char next = requestTarget.charAt(i + 1);
                if (next == '/') {
                    return false;
                }
                if (next == '.') {
                    if (i >= requestTarget.length() - 2) {
                        return false;
                    }
                    char nextNext = requestTarget.charAt(i + 2);
                    if (nextNext == '.' || nextNext == '/' || nextNext == '?' || nextNext == '#') {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Determine whether the given HTTP request target is a relative URI (path+query) appropriate
     * for {@link #toValidPath(String)}. The invariants are:
     *
     * <ul>
     *     <li>This method returns {@code true} exactly when, according to the whatwg URL spec, this
     *     URL has no scheme</li>
     *     <li>If the input is a valid URI, this method is equal to the inverse of
     *     {@link URI#isAbsolute()}</li>
     *     <li>If this method returns {@code true}, and the input is a valid URI after going
     *     through {@link #toValidPath(String)}, {@link URI#isAbsolute()} is {@code false}</li>
     * </ul>
     *
     * @param requestTarget The HTTP request target
     * @return {@code true} if this URL is relative
     */
    public static boolean isRelative(@NonNull String requestTarget) {
        // yes this code is weird. There's a fuzz test that checks it against the whatwg spec
        boolean start = true;
        for (int i = 0; i < requestTarget.length(); i++) {
            char c = requestTarget.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                // newline and tab is ignored anywhere.
                continue;
            }
            if (isAsciiLowerAlpha(c) || isAsciiUpperAlpha(c)) {
                start = false;
                continue;
            }
            if (!start) {
                if (c == ':') {
                    return false;
                }
                if (isAsciiDigit(c) || c == '+' || c == '-' || c == '.') {
                    continue;
                }
                if (isC0OrSpace(c)) {
                    // c0 and space are trimmed at start and end, so we are either invalid or at
                    // the end
                    break;
                }
            } else {
                if (isC0OrSpace(c)) {
                    // c0 and space are trimmed at start and end.
                    continue;
                }
            }
            break;
        }
        return true;
    }

    private static boolean isC0(int c) {
        return c <= 0x1f;
    }

    private static boolean isC0OrSpace(char c) {
        return isC0(c) || c == ' ';
    }

    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiUpperHexDigit(int c) {
        return isAsciiDigit(c) || (c >= 'A' && c <= 'F');
    }

    private static boolean isAsciiLowerHexDigit(int c) {
        return isAsciiDigit(c) || (c >= 'a' && c <= 'f');
    }

    private static boolean isAsciiHexDigit(int c) {
        return isAsciiLowerHexDigit(c) || isAsciiUpperHexDigit(c);
    }

    private static boolean isAsciiUpperAlpha(int c) {
        return c >= 'A' && c <= 'Z';
    }

    private static boolean isAsciiLowerAlpha(int c) {
        return c >= 'a' && c <= 'z';
    }
}
