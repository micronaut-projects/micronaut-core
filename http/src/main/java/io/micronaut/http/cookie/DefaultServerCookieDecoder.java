/*
 * Copyright 2017-2024 original authors
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

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ServerCookieDecoder} implementation that parses an HTTP {@code Cookie} request header into
 * its individual name/value pairs, as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc6265#section-4.2.1">RFC 6265, Section 4.2.1</a>.
 *
 * @author Sergio del Amo
 * @since 4.3.0
 */
@Internal
public final class DefaultServerCookieDecoder implements ServerCookieDecoder {
    @Override
    public List<Cookie> decode(String header) {
        if (header == null || header.isEmpty()) {
            return List.of();
        }
        List<Cookie> cookies = new ArrayList<>();
        int length = header.length();
        int pos = 0;
        while (pos < length) {
            int semicolon = header.indexOf(';', pos);
            int end = semicolon == -1 ? length : semicolon;
            addCookie(header, pos, end, cookies);
            pos = end + 1;
        }
        return cookies;
    }

    private static void addCookie(String header, int start, int end, List<Cookie> cookies) {
        while (start < end && header.charAt(start) == ' ') {
            start++;
        }
        while (end > start && header.charAt(end - 1) == ' ') {
            end--;
        }
        if (start == end) {
            return;
        }
        int equals = header.indexOf('=', start);
        if (equals == -1 || equals >= end) {
            return;
        }
        String name = header.substring(start, equals).trim();
        if (name.isEmpty()) {
            return;
        }
        String value = unwrapValue(header.substring(equals + 1, end).trim());
        try {
            cookies.add(Cookie.of(name, value));
        } catch (IllegalArgumentException e) {
            // skip a single malformed pair rather than rejecting the whole header
        }
    }

    private static String unwrapValue(String value) {
        int length = value.length();
        if (length >= 2 && value.charAt(0) == '"' && value.charAt(length - 1) == '"') {
            return value.substring(1, length - 1);
        }
        return value;
    }
}
