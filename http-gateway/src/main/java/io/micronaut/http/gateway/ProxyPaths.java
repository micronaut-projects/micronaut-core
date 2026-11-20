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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The paths and queries of the requests a gateway relays: the check that a path routes the same
 * way at the gateway and at the upstream, and the edits of the proxy transforms. Every path is
 * the raw, percent-encoded path, as the router matched it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ProxyPaths {

    private static final String EMPTY_LEADING_SEGMENT = "An empty leading segment in the path";

    private ProxyPaths() {
    }

    /**
     * Whether a raw path is one the gateway relays: the router matched it as it is, without
     * removing dot segments or decoding it, and so did the security filters, so a path that an
     * upstream server could resolve to another path must not be relayed. Rejected:
     * <ul>
     *     <li>a {@code .} or {@code ..} segment, also percent-encoded, e.g. {@code %2e%2e};</li>
     *     <li>an encoded slash or backslash, {@code %2F} or {@code %5C}, and a backslash;</li>
     *     <li>a semicolon, the start of path parameters, e.g. {@code ..;/} that some servers
     *     resolve as {@code ..};</li>
     *     <li>an encoded or raw control character, e.g. {@code %00} or {@code %0A}, and a
     *     malformed percent-encoding;</li>
     *     <li>a path that does not start with a slash.</li>
     * </ul>
     * An upstream path is checked with {@link #upstreamRejection(String)}, which also rejects an
     * empty leading segment.
     *
     * @param rawPath The raw path
     * @return The reason it is rejected, or {@code null} if it is relayed
     */
    static @Nullable String rejection(String rawPath) {
        if (rawPath.isEmpty() || rawPath.charAt(0) != '/') {
            return "The path must start with a slash";
        }
        int length = rawPath.length();
        int segmentStart = 1;
        for (int i = 1; i <= length; i++) {
            if (i == length || rawPath.charAt(i) == '/') {
                String rejected = segmentRejection(rawPath, segmentStart, i);
                if (rejected != null) {
                    return rejected;
                }
                segmentStart = i + 1;
            }
        }
        return null;
    }

    /**
     * Whether a raw path is one the gateway sends to an upstream, see {@link #rejection(String)}:
     * a path with an empty leading segment, one that starts with {@code //}, is also rejected,
     * since a URI reads it as a network-path reference whose first segment is an authority, e.g.
     * {@code //attacker.example/x}. Empty segments after the first are relayed as sent.
     *
     * @param rawPath The raw path
     * @return The reason it is rejected, or {@code null} if it is relayed
     */
    static @Nullable String upstreamRejection(String rawPath) {
        if (rawPath.startsWith("//")) {
            return EMPTY_LEADING_SEGMENT;
        }
        return rejection(rawPath);
    }

    private static @Nullable String segmentRejection(String path, int start, int end) {
        int dots = 0;
        int decodedLength = 0;
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
                    return "A path parameter (;) in the path";
                }
            }
            if (decoded < 0x20 || decoded == 0x7f) {
                return "A control character in the path";
            }
            if (decoded == '.') {
                dots++;
            }
            decodedLength++;
        }
        if (decodedLength > 0 && dots == decodedLength && decodedLength <= 2) {
            return "A dot segment in the path";
        }
        return null;
    }

    /**
     * @param rawPath A raw path starting with a slash
     * @return Its segments, raw
     */
    static List<String> segments(String rawPath) {
        List<String> segments = new ArrayList<>();
        int start = rawPath.startsWith("/") ? 1 : 0;
        int length = rawPath.length();
        if (start >= length) {
            return segments;
        }
        for (int i = start; i <= length; i++) {
            if (i == length || rawPath.charAt(i) == '/') {
                segments.add(rawPath.substring(start, i));
                start = i + 1;
            }
        }
        return segments;
    }

    /**
     * Remove leading segments, like Spring Cloud Gateway's {@code StripPrefix}: a path with
     * fewer segments becomes {@code /}. A trailing slash is kept.
     *
     * @param rawPath The raw path
     * @param parts   The number of segments to remove
     * @return The path
     */
    static String stripPrefix(String rawPath, int parts) {
        if (parts <= 0) {
            return rawPath;
        }
        int index = 0;
        for (int i = 0; i < parts; i++) {
            int next = rawPath.indexOf('/', index + 1);
            if (next < 0) {
                return "/";
            }
            index = next;
        }
        return rawPath.substring(index);
    }

    /**
     * @param prefix  A path prefix, e.g. {@code /v2} or {@code /v2/}
     * @param rawPath The raw path
     * @return The path with the prefix
     */
    static String prefix(String prefix, String rawPath) {
        String p = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        if (!p.isEmpty() && p.charAt(0) != '/') {
            p = '/' + p;
        }
        return p + (rawPath.startsWith("/") ? rawPath : "/" + rawPath);
    }

    /**
     * The URI of an upstream request, a path and a query only: a path that starts with
     * {@code //} would make the URI a network-path reference, whose first segment is the
     * authority the client sends the request to.
     *
     * @param path  The raw path
     * @param query The raw query, or {@code null}
     * @return The URI
     * @throws UpstreamPathException if the path has an empty leading segment
     */
    static URI uri(String path, @Nullable String query) {
        if (path.startsWith("//")) {
            throw new UpstreamPathException(EMPTY_LEADING_SEGMENT);
        }
        URI uri = URI.create(pathAndQuery(path, query));
        if (uri.getScheme() != null || uri.getRawAuthority() != null) {
            throw new UpstreamPathException("The upstream path is not a path");
        }
        return uri;
    }

    /**
     * @param path  The raw path
     * @param query The raw query, or {@code null}
     * @return The path and the query, as the URI of a request; an empty query keeps its {@code ?}
     */
    static String pathAndQuery(String path, @Nullable String query) {
        // an empty query, a ? alone, is relayed as it was sent
        return query == null ? path : path + '?' + query;
    }

    /**
     * @param value A value
     * @return The value encoded for a query, with {@code %20} for a space
     */
    static String encodeQueryComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Remove the parameters with a name from a raw query, comparing the decoded names.
     *
     * @param rawQuery The raw query, or {@code null}
     * @param name     The decoded name
     * @return The query, or {@code null} if nothing is left
     */
    static @Nullable String removeQueryParameter(@Nullable String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return rawQuery;
        }
        StringBuilder kept = new StringBuilder(rawQuery.length());
        for (String pair : rawQuery.split("&", -1)) {
            int equals = pair.indexOf('=');
            String rawName = equals < 0 ? pair : pair.substring(0, equals);
            String decoded;
            try {
                decoded = java.net.URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                decoded = rawName;
            }
            if (!decoded.equals(name)) {
                if (!kept.isEmpty()) {
                    kept.append('&');
                }
                kept.append(pair);
            }
        }
        return kept.isEmpty() ? null : kept.toString();
    }

    /**
     * @param rawQuery The raw query, or {@code null}
     * @param name     The decoded name
     * @param value    The decoded value
     * @return The query with every occurrence of the parameter replaced by one with the value,
     * where the first one was; the query as it is if it has no such parameter
     */
    static @Nullable String replaceQueryParameter(@Nullable String rawQuery, String name, String value) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return rawQuery;
        }
        StringBuilder kept = new StringBuilder(rawQuery.length());
        boolean replaced = false;
        for (String pair : rawQuery.split("&", -1)) {
            int equals = pair.indexOf('=');
            String rawName = equals < 0 ? pair : pair.substring(0, equals);
            String decoded;
            try {
                decoded = java.net.URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                decoded = rawName;
            }
            String next;
            if (decoded.equals(name)) {
                if (replaced) {
                    continue;
                }
                replaced = true;
                next = encodeQueryComponent(name) + '=' + encodeQueryComponent(value);
            } else {
                next = pair;
            }
            if (!kept.isEmpty()) {
                kept.append('&');
            }
            kept.append(next);
        }
        return replaced ? kept.toString() : rawQuery;
    }

    /**
     * @param rawQuery The raw query, or {@code null}
     * @param name     The decoded name
     * @param value    The decoded value
     * @return The query with the parameter appended
     */
    static String addQueryParameter(@Nullable String rawQuery, String name, String value) {
        String pair = encodeQueryComponent(name) + '=' + encodeQueryComponent(value);
        return rawQuery == null || rawQuery.isEmpty() ? pair : rawQuery + '&' + pair;
    }

    /**
     * An upstream path that is not relayed, see {@link #uri(String, String)}.
     */
    static final class UpstreamPathException extends IllegalArgumentException {
        UpstreamPathException(String message) {
            super(message);
        }
    }
}
