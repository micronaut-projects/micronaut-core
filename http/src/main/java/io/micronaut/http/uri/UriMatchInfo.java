/*
 * Copyright 2017-2020 original authors
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

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The result of a call to {@link UriMatchTemplate#match(java.net.URI)}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface UriMatchInfo {

    /**
     * @return The matched URI
     */
    String getUri();

    /**
     * @return The variable values following a successful match
     */
    Map<String, Object> getVariableValues();

    /**
     * @return The list of template variables
     */
    List<UriMatchVariable> getVariables();

    /**
     * @return A map of the variables.
     */
    Map<String, UriMatchVariable> getVariableMap();

    /**
     * The captures of a match by a {@link RouteTemplate}, which this match info projects: every
     * occurrence of a repeated variable, in the order of the template, and, if the template
     * reports them, the positions of the values in the path, see {@link RouteCaptures#start(int)}.
     * For a route of a located target, the captures of its template in the rest of the path
     * after the prefixes of the locator routes.
     *
     * @return The captures, or {@code null} if the match was not made by a {@link RouteTemplate}
     * that reports them, e.g. by a {@link UriMatchTemplate}
     * @since 5.3.0
     */
    default @Nullable RouteCaptures getRouteCaptures() {
        return null;
    }
}
