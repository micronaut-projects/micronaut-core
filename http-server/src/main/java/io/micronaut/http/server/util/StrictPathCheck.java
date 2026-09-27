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
package io.micronaut.http.server.util;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * The strict check of a raw request path: the router matches the raw, percent-encoded path as
 * it is, without removing dot segments or decoding it, and so do the filters that secure a path,
 * so a path that another server, e.g. the upstream of a proxy route, could resolve to another
 * path is ambiguous. See {@code micronaut.server.strict-path-check}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class StrictPathCheck {

    private StrictPathCheck() {
    }

    /**
     * Whether a raw path passes the strict check. Rejected:
     * <ul>
     *     <li>a {@code .} or {@code ..} segment, also percent-encoded, e.g. {@code %2e%2e};</li>
     *     <li>an encoded slash or backslash, {@code %2F} or {@code %5C}, and a backslash;</li>
     *     <li>a semicolon, the start of path parameters, e.g. {@code ..;/} that some servers
     *     resolve as {@code ..}, unless {@code allowSemicolon}: then the part of a segment before
     *     its first semicolon must not be a dot segment;</li>
     *     <li>an encoded or raw control character, e.g. {@code %00} or {@code %0A}, a malformed
     *     percent-encoding, and the first byte of an overlong UTF-8 sequence, {@code %C0} or
     *     {@code %C1};</li>
     *     <li>a path that does not start with a slash.</li>
     * </ul>
     *
     * @param rawPath        The raw, percent-encoded path
     * @param allowSemicolon Whether a semicolon, the start of path parameters, is allowed
     * @return The reason the path is rejected, or {@code null} if it passes
     */
    public static @Nullable String rejection(String rawPath, boolean allowSemicolon) {
        if (rawPath.isEmpty() || rawPath.charAt(0) != '/') {
            return "The path must start with a slash";
        }
        int length = rawPath.length();
        int segmentStart = 1;
        for (int i = 1; i <= length; i++) {
            if (i == length || rawPath.charAt(i) == '/') {
                String rejected = segmentRejection(rawPath, segmentStart, i, allowSemicolon);
                if (rejected != null) {
                    return rejected;
                }
                segmentStart = i + 1;
            }
        }
        return null;
    }

    private static @Nullable String segmentRejection(String path, int start, int end, boolean allowSemicolon) {
        int dots = 0;
        int decodedLength = 0;
        // with path parameters allowed, the name of the segment ends at the first semicolon
        boolean inParameters = false;
        for (int i = start; i < end; i++) {
            char c = path.charAt(i);
            char decoded;
            if (c == '%') {
                if (i + 2 >= end) {
                    return "Malformed percent-encoding in the path";
                }
                int high = Character.digit(path.charAt(i + 1), 16);
                int low = Character.digit(path.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    return "Malformed percent-encoding in the path";
                }
                decoded = (char) (high * 16 + low);
                i += 2;
                if (decoded == '/' || decoded == '\\') {
                    return "An encoded slash or backslash in the path";
                }
                if (decoded == 0xC0 || decoded == 0xC1) {
                    // the first byte of an overlong UTF-8 sequence, e.g. %C0%AE for a dot
                    return "An invalid UTF-8 sequence in the path";
                }
                if (decoded == ';') {
                    // decoded, it is a character of the segment, not a path parameter
                    decoded = 'x';
                }
            } else {
                decoded = c;
                if (c == '\\') {
                    return "A backslash in the path";
                }
                if (c == ';') {
                    if (!allowSemicolon) {
                        return "A path parameter (;) in the path";
                    }
                    inParameters = true;
                }
            }
            if (decoded < 0x20 || decoded == 0x7f) {
                return "A control character in the path";
            }
            if (!inParameters) {
                if (decoded == '.') {
                    dots++;
                }
                decodedLength++;
            }
        }
        if (decodedLength > 0 && dots == decodedLength && decodedLength <= 2) {
            return "A dot segment in the path";
        }
        return null;
    }
}
