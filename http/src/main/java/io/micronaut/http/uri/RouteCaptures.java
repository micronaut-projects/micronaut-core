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
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The values a {@link RouteTemplate} captured for its variables, in the order of the
 * {@link RouteTemplate#variables() variables}. The values are as they appear in the path,
 * not percent-decoded. A template may also report where each value is in the
 * {@link #path() path}, see {@link #start(int)}: what a framework needs to tell which segments
 * of the path a variable captured, e.g. for the path segments or the matched URIs of JAX-RS.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface RouteCaptures permits DefaultRouteCaptures {

    /**
     * Captures of copies of the lists.
     *
     * @param path      The matched path
     * @param variables The variables of the template
     * @param values    The captured values, one per variable, {@code null} for an optional variable
     *                  that is absent
     * @return The captures
     */
    static RouteCaptures copyOf(String path, List<RouteTemplateVariable> variables, List<@Nullable String> values) {
        return new DefaultRouteCaptures(path, List.copyOf(variables), Collections.unmodifiableList(new ArrayList<>(values)));
    }

    /**
     * Captures that take the lists as they are, without copying them: for a template that matches
     * many requests. The caller hands them over: they must not change afterwards. Pass the list of
     * {@link RouteTemplate#variables()} itself, the same instance, which the router then
     * recognizes without comparing it.
     *
     * @param path      The path that was matched
     * @param variables The variables, in order: an immutable list
     * @param values    The values, one per variable, not percent-decoded: a list that does not change
     * @return The captures
     */
    static RouteCaptures of(String path, List<RouteTemplateVariable> variables, List<@Nullable String> values) {
        return new DefaultRouteCaptures(path, variables, Collections.unmodifiableList(values));
    }

    /**
     * Captures with the positions of the values in the path, see {@link #start(int)}, that take
     * the lists and the array as they are, without copying them, like
     * {@link #of(String, List, List)}.
     *
     * @param path      The path that was matched
     * @param variables The variables, in order: an immutable list
     * @param values    The values, one per variable, not percent-decoded: a list that does not change
     * @param offsets   The start and the end offset of each value in the path, two per variable:
     *                  {@code offsets[2 * i]} is the start of the value of the variable {@code i},
     *                  {@code offsets[2 * i + 1]} its end, exclusive; {@code -1} for both if the
     *                  value is absent. An array that does not change
     * @return The captures
     * @throws IllegalArgumentException if there are not two offsets per variable, or the offsets
     * of a value do not delimit it in the path
     * @since 5.3.0
     */
    static RouteCaptures of(String path, List<RouteTemplateVariable> variables, List<@Nullable String> values, int[] offsets) {
        return new DefaultRouteCaptures(path, variables, Collections.unmodifiableList(values), offsets);
    }

    /**
     * @return The matched path
     */
    String path();

    /**
     * @return The variables, in the order of the template
     */
    List<RouteTemplateVariable> variables();

    /**
     * @return The captured values, in the order of the variables
     */
    List<@Nullable String> values();

    /**
     * @param name The name of a variable
     * @return The value of its first occurrence, or {@code null} if absent
     */
    @Nullable String value(String name);

    /**
     * @param name The name of a variable
     * @return The values of every occurrence of the name, in the order of the template, an
     * absent one left out: more than one for a template that repeats the name
     * @since 5.3.0
     */
    List<String> values(String name);

    /**
     * @return Whether the template reported where the values are in the {@link #path() path},
     * see {@link #start(int)}
     * @since 5.3.0
     */
    boolean hasOffsets();

    /**
     * Where the value of a variable starts in the {@link #path() path}: {@code path().substring(start(i), end(i))}
     * is the value of the variable {@code i}, and the path before it is what the template matched
     * before the variable, e.g. to count the segments a variable or the part of the template
     * before it matched.
     *
     * @param index The index of the variable, in the order of the {@link #variables() variables}
     * @return The offset of the first character of the value in the path, or {@code -1} if the
     * value is absent or the template did not report it, see {@link #hasOffsets()}
     * @throws IndexOutOfBoundsException if there is no variable at the index
     * @since 5.3.0
     */
    int start(int index);

    /**
     * Where the value of a variable ends in the {@link #path() path}, see {@link #start(int)}.
     *
     * @param index The index of the variable, in the order of the {@link #variables() variables}
     * @return The offset after the last character of the value in the path, or {@code -1} if
     * the value is absent or the template did not report it, see {@link #hasOffsets()}
     * @throws IndexOutOfBoundsException if there is no variable at the index
     * @since 5.3.0
     */
    int end(int index);

    /**
     * The captures as the {@link UriMatchInfo} of the existing route match API: a compatibility
     * projection in which each name has the value of its first occurrence, absent values are left
     * out, and the variables are described as {@link UriMatchVariable}s.
     *
     * @return The match info
     */
    UriMatchInfo toUriMatchInfo();

    /**
     * The captures as the {@link UriMatchInfo} of the existing route match API, see
     * {@link #toUriMatchInfo()}, with the variables already described.
     *
     * @param uriMatchVariables The variables of the captures as {@link UriMatchVariable}s, see {@link #uriMatchVariables(List)}
     * @param uriMatchVariableMap The variables by name, see {@link #uriMatchVariableMap(List)}
     * @return The match info
     */
    @Internal
    UriMatchInfo toUriMatchInfo(List<UriMatchVariable> uriMatchVariables, Map<String, UriMatchVariable> uriMatchVariableMap);

    /**
     * @param variables The variables of a template
     * @return The variables as the {@link UriMatchVariable}s of the existing route match API
     */
    @Internal
    static List<UriMatchVariable> uriMatchVariables(List<RouteTemplateVariable> variables) {
        return DefaultRouteCaptures.uriMatchVariables(variables);
    }

    /**
     * @param uriMatchVariables The variables of a template as {@link UriMatchVariable}s, see {@link #uriMatchVariables(List)}
     * @return The unmodifiable map of the variables by name, shared by the matches of the template: the last variable of a
     * repeated name, in the order of the first
     */
    @Internal
    static Map<String, UriMatchVariable> uriMatchVariableMap(List<UriMatchVariable> uriMatchVariables) {
        return DefaultUriMatchInfo.variableMap(uriMatchVariables);
    }
}
