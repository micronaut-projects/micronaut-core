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

/**
 * A path pattern of a gateway route, see {@link GatewayRouteBuilder}, as the template of a
 * functional route.
 *
 * @param template The URI template, or the prefix of a proxy route
 * @param prefix   Whether the pattern is a prefix and every path under it, {@code /prefix/**}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record GatewayPathPattern(String template, boolean prefix) {

    private static final String ANY_PATH = "/**";

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
            return new GatewayPathPattern("/", true);
        }
        if (p.endsWith(ANY_PATH)) {
            prefix = true;
            p = p.substring(0, p.length() - ANY_PATH.length());
        }
        StringBuilder template = new StringBuilder(p.length() + 8);
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
                    template.append("/{_w").append(wildcards++).append('}');
                } else if (segment.contains("**")) {
                    throw new IllegalArgumentException("A gateway path pattern has ** only at its end, as /**: " + pattern);
                } else if (hasWildcard(segment)) {
                    throw new IllegalArgumentException("A gateway path pattern has * only as a whole segment, and no ?: " + pattern);
                } else {
                    template.append('/').append(segment);
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
        return new GatewayPathPattern(result, prefix);
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
