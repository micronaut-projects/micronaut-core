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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A path pattern of a gateway route, see {@link GatewayRouteBuilder}, as the template of a
 * functional route.
 *
 * @param template The URI template, or the prefix of a proxy route
 * @param prefix    Whether the pattern is a prefix and every path under it, {@code /prefix/**}
 * @param raw       A pattern of the raw paths the template matches, whose groups {@code v0},
 *                  {@code v1}… capture the raw values of the variables
 * @param variables The names of the variables, in the order of the groups
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record GatewayPathPattern(String template, boolean prefix, Pattern raw, List<String> variables) {

    private static final String ANY_PATH = "/**";
    /**
     * The expression of a variable that is a whole segment: a simple variable of a URI template
     * does not match {@code +}, {@code &} or {@code ;}, which a segment of a path may have.
     */
    private static final String ANY_SEGMENT = ":[^/]+";

    /**
     * @param pattern A path pattern
     * @return The pattern
     * @throws IllegalArgumentException if it is not a pattern the gateway supports
     */
    static GatewayPathPattern parse(String pattern) {
        String p = pattern.strip();
        if (!p.startsWith("/")) {
            throw new IllegalArgumentException("A gateway path pattern starts with a slash: " + pattern);
        }
        boolean prefix = false;
        if (p.equals(ANY_PATH)) {
            return new GatewayPathPattern("/", true, Pattern.compile("(?:/.*)?"), List.of());
        }
        if (p.endsWith(ANY_PATH)) {
            prefix = true;
            p = p.substring(0, p.length() - ANY_PATH.length());
        }
        StringBuilder template = new StringBuilder(p.length() + 8);
        StringBuilder raw = new StringBuilder(p.length() + 16);
        List<String> variables = new ArrayList<>();
        int wildcards = 0;
        int depth = 0;
        int segmentStart = 0;
        for (int i = 0; i <= p.length(); i++) {
            char c = i < p.length() ? p.charAt(i) : '/';
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            if (depth == 0 && c == '/' && i > segmentStart) {
                String segment = p.substring(segmentStart + 1, i);
                if (segment.equals("*")) {
                    template.append("/{_w").append(wildcards++).append(ANY_SEGMENT).append('}');
                    raw.append("/[^/]+");
                } else if (segment.length() > 2 && segment.charAt(0) == '{' && segment.charAt(segment.length() - 1) == '}'
                    && segment.indexOf('{', 1) < 0) {
                    int colon = segment.indexOf(':');
                    String name = segment.substring(1, colon < 0 ? segment.length() - 1 : colon);
                    if (colon < 0) {
                        // a variable of the whole segment matches every character of a segment, e.g. +
                        template.append('/').append(segment, 0, segment.length() - 1).append(ANY_SEGMENT).append('}');
                    } else {
                        template.append('/').append(segment);
                    }
                    raw.append("/(?<v").append(variables.size()).append(">[^/]+)");
                    variables.add(name);
                } else if (segment.contains("**")) {
                    throw new IllegalArgumentException("A gateway path pattern has ** only at its end, as /**: " + pattern);
                } else if (hasWildcard(segment)) {
                    throw new IllegalArgumentException("A gateway path pattern has * only as a whole segment, and no ?: " + pattern);
                } else {
                    template.append('/').append(segment);
                    raw.append('/').append(segment.indexOf('{') >= 0 ? "[^/]+" : Pattern.quote(segment));
                }
                segmentStart = i;
            } else if (depth == 0 && c == '/') {
                segmentStart = i;
            }
        }
        if (p.endsWith("/") && p.length() > 1) {
            template.append('/');
        }
        String result = template.isEmpty() ? "/" : template.toString();
        // the router ignores a trailing slash; a prefix matches the prefix and every path under it
        raw.append(prefix ? "(?:/.*)?" : "/?");
        return new GatewayPathPattern(result, prefix, Pattern.compile(raw.toString()), List.copyOf(variables));
    }

    /**
     * @param rawPath A raw path
     * @return The raw values of the variables of the pattern in the path, or {@code null} if the
     * pattern does not match it
     */
    @Nullable Map<String, String> rawVariables(String rawPath) {
        Matcher matcher = raw.matcher(rawPath);
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            values.put(variables.get(i), matcher.group("v" + i));
        }
        return values;
    }

    private static boolean hasWildcard(String segment) {
        int depth = 0;
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0 && (c == '*' || c == '?')) {
                return true;
            }
        }
        return false;
    }
}
