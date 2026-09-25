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

import java.util.Objects;

/**
 * A variable of a {@link ParsedRouteTemplate}, described independently of the template language.
 *
 * @param name     The name of the variable
 * @param optional Whether a path without the variable can still match the template
 * @param location Where the variable is captured from
 * @param exploded Whether the variable captures a list of values, e.g. {@code {path*}}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record RouteTemplateVariable(String name, boolean optional, Location location, boolean exploded) {

    /**
     * @param name     The name of the variable
     * @param optional Whether a path without the variable can still match the template
     * @param location Where the variable is captured from
     * @param exploded Whether the variable captures a list of values
     */
    public RouteTemplateVariable {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }

    /**
     * A required variable of the path.
     *
     * @param name The name of the variable
     * @return The variable
     */
    public static RouteTemplateVariable path(String name) {
        return new RouteTemplateVariable(name, false, Location.PATH, false);
    }

    /**
     * Where a variable is captured from.
     */
    public enum Location {
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
