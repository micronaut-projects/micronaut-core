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

import io.micronaut.core.annotation.Internal;
import java.util.Objects;

/**
 * The default {@link RouteTemplateVariable}.
 *
 * @param name     The name of the variable
 * @param optional Whether a path without the variable can still match the template
 * @param location Where the variable is captured from
 * @param exploded Whether the variable captures a list of values
 * @param pattern  Whether a regular expression constrains the value
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultRouteTemplateVariable(String name, boolean optional, Location location, boolean exploded, boolean pattern) implements RouteTemplateVariable {

    /**
     * @param name     The name of the variable
     * @param optional Whether a path without the variable can still match the template
     * @param location Where the variable is captured from
     * @param exploded Whether the variable captures a list of values
     * @param pattern  Whether a regular expression constrains the value
     */
    DefaultRouteTemplateVariable {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
