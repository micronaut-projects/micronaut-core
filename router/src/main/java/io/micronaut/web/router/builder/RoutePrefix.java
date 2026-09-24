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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The prefix of the URI templates of the routes of a group, see
 * {@link HttpRouteBuilder#path(String, java.util.function.Consumer)}: it joins the prefix and the
 * URI template of a route like the URI of a controller and of its method are joined. Every URI
 * template of a group is prefixed here, so a template syntax that cannot be joined as a string is
 * rejected, or joined its own way, in this one place.
 *
 * @param value The prefix: starts with a slash and does not end with one
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record RoutePrefix(String value) {

    private static final char SLASH = '/';
    private static final char VARIABLE_START = '{';

    /**
     * The prefix of a group, nested in the group of the enclosing prefix.
     *
     * @param prefix    The prefix of the group
     * @param enclosing The prefix of the enclosing group, or {@code null}
     * @return The prefix, or {@code null} if neither group has one
     */
    static @Nullable RoutePrefix of(String prefix, @Nullable RoutePrefix enclosing) {
        Objects.requireNonNull(prefix, "prefix");
        if (prefix.indexOf('?') >= 0 || prefix.indexOf('#') >= 0
            || prefix.contains("{?") || prefix.contains("{&") || prefix.contains("{#")) {
            throw new IllegalArgumentException("The prefix of a route group is a path, without a query or a fragment: " + prefix);
        }
        String normalized = prefix.strip();
        while (!normalized.isEmpty() && normalized.charAt(normalized.length() - 1) == SLASH) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            // the root: no prefix
            return enclosing;
        }
        if (normalized.charAt(0) != SLASH) {
            normalized = SLASH + normalized;
        }
        return new RoutePrefix(enclosing == null ? normalized : enclosing.prefix(normalized));
    }

    /**
     * The URI template of a route of the group.
     *
     * @param uri The URI template of the route, relative to the prefix
     * @return The prefix followed by the URI template
     */
    String prefix(String uri) {
        Objects.requireNonNull(uri, "uri");
        if (uri.isEmpty() || uri.equals("/")) {
            // like @Get("/") of a controller: the URI of the controller
            return value;
        }
        char first = uri.charAt(0);
        if (first == '?' || first == '#') {
            throw new IllegalArgumentException("The URI template of a route in a group with the prefix " + value + " must be a path: " + uri);
        }
        if (first == SLASH) {
            return value + uri;
        }
        if (first == VARIABLE_START && uri.length() > 1) {
            char operator = uri.charAt(1);
            if (operator == SLASH || operator == '?' || operator == '&' || operator == '#') {
                // an expression that expands with its own separator, e.g. {/segment} or {?query}
                return value + uri;
            }
        }
        return value + SLASH + uri;
    }

    @Override
    public String toString() {
        return value;
    }
}
