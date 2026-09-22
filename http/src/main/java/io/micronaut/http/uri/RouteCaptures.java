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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The values a {@link RoutePattern} captured for the variables of its template, in the order of the
 * {@link ParsedRouteTemplate#variables() variables}. The values are as they appear in the path,
 * not percent-decoded.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RouteCaptures {

    private final String path;
    private final List<RouteTemplateVariable> variables;
    private final List<@Nullable String> values;

    /**
     * @param path      The matched path
     * @param variables The variables of the template
     * @param values    The captured values, one per variable, {@code null} for an optional variable
     *                  that is absent
     */
    public RouteCaptures(String path, List<RouteTemplateVariable> variables, List<@Nullable String> values) {
        this.path = Objects.requireNonNull(path, "path");
        this.variables = List.copyOf(variables);
        if (values.size() != variables.size()) {
            throw new IllegalArgumentException("Expected " + variables.size() + " captured values but got " + values.size());
        }
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * @return The matched path
     */
    public String path() {
        return path;
    }

    /**
     * @return The variables, in the order of the template
     */
    public List<RouteTemplateVariable> variables() {
        return variables;
    }

    /**
     * @return The captured values, in the order of the variables
     */
    public List<@Nullable String> values() {
        return values;
    }

    /**
     * @param name The name of a variable
     * @return The value of its first occurrence, or {@code null} if absent
     */
    public @Nullable String value(String name) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).name().equals(name)) {
                return values.get(i);
            }
        }
        return null;
    }

    /**
     * The captures as the {@link UriMatchInfo} of the existing route match API: a compatibility
     * projection in which each name has the value of its first occurrence, absent values are left
     * out, and the variables are described as {@link UriMatchVariable}s.
     *
     * @return The match info
     */
    public UriMatchInfo toUriMatchInfo() {
        Map<String, Object> valueMap = LinkedHashMap.newLinkedHashMap(values.size());
        List<UriMatchVariable> legacy = new ArrayList<>(variables.size());
        for (int i = 0; i < variables.size(); i++) {
            RouteTemplateVariable variable = variables.get(i);
            legacy.add(toUriMatchVariable(variable));
            String value = values.get(i);
            if (value != null) {
                valueMap.putIfAbsent(variable.name(), value);
            }
        }
        return new DefaultUriMatchInfo(path, valueMap, legacy);
    }

    private static UriMatchVariable toUriMatchVariable(RouteTemplateVariable variable) {
        char operator;
        if (variable.location() == RouteTemplateVariable.Location.QUERY) {
            operator = '?';
        } else if (variable.optional()) {
            operator = '/';
        } else {
            operator = '0';
        }
        return new UriMatchVariable(variable.name(), variable.exploded() ? '*' : '0', operator);
    }

    @Override
    public String toString() {
        return path + ' ' + values;
    }
}
