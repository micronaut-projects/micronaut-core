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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;

/**
 * A URL parser generated at compile time for an enum of {@link RouteDeclaration}s: it reads the
 * request path segment by segment with generated code and answers the matched declaration, whose
 * ordinal selects the bound route directly. The router asks it before its own matching, so a
 * request costs one pass over the path and an array index.
 *
 * <p>A matcher answers only routes it can match exactly as the router would: templates made of
 * literal segments and whole-segment variables such as {@code /pets/{id}/owners/{owner}}. Other
 * declarations of the enum are matched by the router.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see RouteDeclaration#matcher()
 */
@Experimental
public interface CompiledRouteMatcher {

    /**
     * Match a request.
     *
     * @param method    The HTTP method
     * @param path      The path, without the query and without a trailing slash, except for the root path
     * @param variables Receives the raw values of the path variables of the matched route, in the
     *                  order of its template; at least {@link #maxVariables()} long
     * @return The ordinal of the matched declaration, or {@code -1}
     */
    int match(HttpMethod method, String path, String[] variables);

    /**
     * @return The largest number of path variables of a route the matcher answers
     */
    int maxVariables();
}
