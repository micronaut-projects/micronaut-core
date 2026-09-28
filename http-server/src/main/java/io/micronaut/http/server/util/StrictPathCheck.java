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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The strict check of a raw request path: the router matches the raw, percent-encoded path as
 * it is, without removing dot segments or decoding it, and so do the filters that secure a path,
 * so a path that another server, e.g. the upstream of a proxy route, could resolve to another
 * path is ambiguous. See {@code micronaut.server.strict-path-check}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class StrictPathCheck {

    private static final String MALFORMED = "Malformed percent-encoding in the path";
    private static final String CONTROL = "A control character in the path";

    private StrictPathCheck() {
    }

    /**
     * Whether a raw path passes the strict check. Rejected:
     * <ul>
     *     <li>a {@code .} or {@code ..} segment, also percent-encoded, e.g. {@code %2e%2e};</li>
     *     <li>an encoded slash or backslash, {@code %2F} or {@code %5C}, and a backslash;</li>
     *     <li>an encoded percent sign, {@code %25}, which a second decoding turns into another
     *     path, e.g. {@code %252e%252e};</li>
     *     <li>a semicolon, raw or encoded, the start of path parameters, e.g. {@code ..;/} that
     *     some servers resolve as {@code ..}, unless {@code allowSemicolon}: then the part of a
     *     segment before its first semicolon must not be a dot segment;</li>
     *     <li>a raw {@code #}, the start of a fragment, and any raw character outside ASCII;</li>
     *     <li>an encoded or raw control character, C0, {@code DEL} or C1, e.g. {@code %00},
     *     {@code %0A} or {@code %C2%85};</li>
     *     <li>a malformed percent-encoding and a percent-encoding that is not well-formed UTF-8:
     *     overlong forms, e.g. {@code %C0%AE} or {@code %E0%80%AE}, surrogates, stray
     *     continuation bytes and bytes that never occur in UTF-8;</li>
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
        byte[] buffer = new byte[length];
        int segmentStart = 1;
        for (int i = 1; i <= length; i++) {
            if (i == length || rawPath.charAt(i) == '/') {
                String rejected = segmentRejection(rawPath, segmentStart, i, allowSemicolon, buffer);
                if (rejected != null) {
                    return rejected;
                }
                segmentStart = i + 1;
            }
        }
        return null;
    }

    private static @Nullable String segmentRejection(String path, int start, int end, boolean allowSemicolon, byte[] buffer) {
        // the decoded bytes of the segment; nameEnd is where its first semicolon starts the path parameters
        int size = 0;
        int nameEnd = -1;
        for (int i = start; i < end; i++) {
            char c = path.charAt(i);
            int b;
            if (c == '%') {
                if (i + 2 >= end) {
                    return MALFORMED;
                }
                int high = Character.digit(path.charAt(i + 1), 16);
                int low = Character.digit(path.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    return MALFORMED;
                }
                b = high * 16 + low;
                i += 2;
                if (b == '/' || b == '\\') {
                    return "An encoded slash or backslash in the path";
                }
                if (b == '%') {
                    return "An encoded percent sign in the path";
                }
            } else {
                if (c >= 0x80) {
                    return "A character outside ASCII in the path";
                }
                if (c == '\\') {
                    return "A backslash in the path";
                }
                if (c == '#') {
                    return "A fragment (#) in the path";
                }
                b = c;
            }
            if (b == ';') {
                // encoded or not: a server that decodes the path first sees path parameters
                if (!allowSemicolon) {
                    return "A path parameter (;) in the path";
                }
                if (nameEnd < 0) {
                    nameEnd = size;
                }
            }
            if (b < 0x20 || b == 0x7f) {
                return CONTROL;
            }
            buffer[size++] = (byte) b;
        }
        String decoded;
        try {
            decoded = newDecoder().decode(ByteBuffer.wrap(buffer, 0, size)).toString();
        } catch (CharacterCodingException e) {
            return "An invalid UTF-8 sequence in the path";
        }
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c >= 0x80 && c <= 0x9f) {
                return CONTROL;
            }
        }
        // a dot segment is one or two ASCII bytes, so a name of that many bytes is also that many chars
        int nameLength = nameEnd < 0 ? decoded.length() : nameEnd;
        if (nameLength == 1 && decoded.charAt(0) == '.'
            || nameLength == 2 && decoded.charAt(0) == '.' && decoded.charAt(1) == '.') {
            return "A dot segment in the path";
        }
        return null;
    }

    private static CharsetDecoder newDecoder() {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    }
}
