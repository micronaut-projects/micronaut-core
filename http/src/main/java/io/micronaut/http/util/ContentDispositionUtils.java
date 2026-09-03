/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.util;

import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Internal;

/**
 * Utility class to build RFC 6266 compliant {@code Content-Disposition} header values.
 *
 * @author Shubham Jain
 * @since 5.2.0
 */
@Internal
public final class ContentDispositionUtils {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private ContentDispositionUtils() {
    }

    /**
     * Builds a {@code Content-Disposition} header value for the given disposition type and, optionally, filename.
     *
     * @param type     The disposition type, typically {@code attachment} or {@code inline}
     * @param filename The filename to include, or {@code null}/empty to omit the filename parameters
     * @return The header value
     * @since 4.10.0
     */
    public static String toHeaderValue(String type, @Nullable String filename) {
        if (StringUtils.isEmpty(filename)) {
            return type;
        }
        // https://httpwg.org/specs/rfc6266.html#advice.generating
        // 'filename' parameter is the fallback for legacy browsers, 'filename*' is the supported approach.
        return type + "; filename=\"" + sanitizeAscii(filename) + "\"; filename*=utf-8''" + encodeRfc5987(filename);
    }

    private static String sanitizeAscii(String s) {
        StringBuilder builder = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // '"' ends the quoted-string early and '\' is its escape character; stripping both
            // keeps this legacy fallback value unambiguous instead of escaping them.
            if (c >= 32 && c < 127 && c != '"' && c != '\\') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    // this is mostly copied from netty QueryStringEncoder

    @SuppressWarnings({"java:S3776", "java:S135", "java:S127"}) // stay close to netty impl
    static String encodeRfc5987(String s) {
        StringBuilder uriBuilder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (dontNeedEncoding(c)) {
                    uriBuilder.append(c);
                } else {
                    appendEncoded(uriBuilder, c);
                }
            } else if (c < 0x800) {
                appendEncoded(uriBuilder, 0xc0 | (c >> 6));
                appendEncoded(uriBuilder, 0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) {
                if (!Character.isHighSurrogate(c)) {
                    appendEncoded(uriBuilder, '?');
                    continue;
                }
                // Surrogate Pair consumes 2 characters.
                if (++i == s.length()) {
                    appendEncoded(uriBuilder, '?');
                    break;
                }
                // Extra method to allow inlining the rest of writeUtf8 which is the most likely code path.
                writeUtf8Surrogate(uriBuilder, c, s.charAt(i));
            } else {
                appendEncoded(uriBuilder, 0xe0 | (c >> 12));
                appendEncoded(uriBuilder, 0x80 | ((c >> 6) & 0x3f));
                appendEncoded(uriBuilder, 0x80 | (c & 0x3f));
            }
        }
        return uriBuilder.toString();
    }

    private static boolean dontNeedEncoding(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9'
                || ch == '-' || ch == '_' || ch == '.' || ch == '*' || ch == '~';
    }

    private static void appendEncoded(StringBuilder uriBuilder, int b) {
        uriBuilder.append('%').append(HEX_DIGITS[(b >> 4) & 0xf]).append(HEX_DIGITS[b & 0xf]);
    }

    private static void writeUtf8Surrogate(StringBuilder uriBuilder, char c, char c2) {
        if (!Character.isLowSurrogate(c2)) {
            appendEncoded(uriBuilder, '?');
            appendEncoded(uriBuilder, Character.isHighSurrogate(c2) ? '?' : c2);
            return;
        }
        int codePoint = Character.toCodePoint(c, c2);
        // See https://www.unicode.org/versions/Unicode7.0.0/ch03.pdf#G2630.
        appendEncoded(uriBuilder, 0xf0 | (codePoint >> 18));
        appendEncoded(uriBuilder, 0x80 | ((codePoint >> 12) & 0x3f));
        appendEncoded(uriBuilder, 0x80 | ((codePoint >> 6) & 0x3f));
        appendEncoded(uriBuilder, 0x80 | (codePoint & 0x3f));
    }
}
