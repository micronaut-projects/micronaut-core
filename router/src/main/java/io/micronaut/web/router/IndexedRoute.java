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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;

/**
 * A URI route that provides the keys the router indexes and orders routes by, without its
 * template being parsed again.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
interface IndexedRoute {

    /**
     * @return A literal every path the route matches starts with, or an empty string, see
     * {@link io.micronaut.http.uri.UriTemplateMatcher#getRequiredPrefix()}
     */
    String getRequiredPathPrefix();

    /**
     * @return The length of the literal parts of the template
     */
    int getRawLength();

    /**
     * @return The number of path variables of the template
     */
    int getPathVariableCount();

    /**
     * The order of routes, the same as {@link io.micronaut.http.uri.UriTemplateMatcher#compareTo}:
     * longer literals first, then fewer variables.
     *
     * @param a A route
     * @param b Another route
     * @return The comparison
     */
    static int compare(IndexedRoute a, IndexedRoute b) {
        int rawCompare = Integer.compare(b.getRawLength(), a.getRawLength());
        if (rawCompare == 0) {
            return Integer.compare(a.getPathVariableCount(), b.getPathVariableCount());
        }
        return rawCompare;
    }
}
