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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Experimental;

/**
 * A variable of a {@link RouteTemplate}, described independently of the template language.
 * Created with {@link #of(String, boolean, Location, boolean, boolean)} or {@link #path(String)}:
 * the interface is sealed, so that it can describe more about a variable later.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface RouteTemplateVariable permits DefaultRouteTemplateVariable {

    /**
     * A variable.
     *
     * @param name     The name of the variable
     * @param optional Whether a path without the variable can still match the template
     * @param location Where the variable is captured from
     * @param exploded Whether the variable captures a list of values, e.g. {@code {path*}}
     * @param pattern  Whether a regular expression constrains the value, e.g. {@code {id:\d+}},
     *                 see {@link RouteTemplate#patternVariableCount()}
     * @return The variable
     */
    static RouteTemplateVariable of(String name, boolean optional, Location location, boolean exploded, boolean pattern) {
        return new DefaultRouteTemplateVariable(name, optional, location, exploded, pattern);
    }

    /**
     * A required variable of the path.
     *
     * @param name The name of the variable
     * @return The variable
     */
    static RouteTemplateVariable path(String name) {
        return of(name, false, Location.PATH, false, false);
    }

    /**
     * @return The name of the variable
     */
    String name();

    /**
     * @return Whether a path without the variable can still match the template
     */
    boolean optional();

    /**
     * @return Where the variable is captured from
     */
    Location location();

    /**
     * @return Whether the variable captures a list of values, e.g. {@code {path*}}
     */
    boolean exploded();

    /**
     * @return Whether a regular expression constrains the value, e.g. {@code {id:\d+}}, see
     * {@link RouteTemplate#patternVariableCount()}
     */
    boolean pattern();

    /**
     * Where a variable is captured from.
     */
    enum Location {
        /**
         * The path of the request.
         */
        PATH,
        /**
         * The query or the fragment of the request URI.
         */
        QUERY
    }
}
