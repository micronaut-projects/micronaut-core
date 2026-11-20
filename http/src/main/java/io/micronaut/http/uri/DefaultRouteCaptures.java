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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The default {@link RouteCaptures}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultRouteCaptures implements RouteCaptures {

    private final String path;
    private final List<RouteTemplateVariable> variables;
    private final List<@Nullable String> values;
    private final int @Nullable [] offsets;

    /**
     * @param path      The matched path
     * @param variables The variables, as the captures keep them
     * @param values    The values, as the captures keep them
     */
    DefaultRouteCaptures(String path, List<RouteTemplateVariable> variables, List<@Nullable String> values) {
        this.path = Objects.requireNonNull(path, "path");
        this.variables = Objects.requireNonNull(variables, "variables");
        if (values.size() != variables.size()) {
            throw new IllegalArgumentException("Expected " + variables.size() + " captured values but got " + values.size());
        }
        this.values = values;
        this.offsets = null;
    }

    /**
     * @param path      The matched path
     * @param variables The variables, as the captures keep them
     * @param values    The values, as the captures keep them
     * @param offsets   The start and end offsets of the values, as the captures keep them
     */
    DefaultRouteCaptures(String path, List<RouteTemplateVariable> variables, List<@Nullable String> values, int[] offsets) {
        this.path = Objects.requireNonNull(path, "path");
        this.variables = Objects.requireNonNull(variables, "variables");
        if (values.size() != variables.size()) {
            throw new IllegalArgumentException("Expected " + variables.size() + " captured values but got " + values.size());
        }
        if (offsets.length != 2 * variables.size()) {
            throw new IllegalArgumentException("Expected " + 2 * variables.size() + " offsets but got " + offsets.length);
        }
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            int start = offsets[2 * i];
            int end = offsets[2 * i + 1];
            boolean valid = value == null
                ? start == -1 && end == -1
                : start >= 0 && end <= path.length() && end - start == value.length() && path.startsWith(value, start);
            if (!valid) {
                throw new IllegalArgumentException("The offsets " + start + ".." + end + " of the variable "
                    + variables.get(i).name() + " do not delimit its value '" + value + "' in the path " + path);
            }
        }
        this.values = values;
        this.offsets = offsets;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public List<RouteTemplateVariable> variables() {
        return variables;
    }

    @Override
    public List<@Nullable String> values() {
        return values;
    }

    @Override
    public @Nullable String value(String name) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).name().equals(name)) {
                return values.get(i);
            }
        }
        return null;
    }

    @Override
    public List<String> values(String name) {
        List<String> result = null;
        for (int i = 0; i < variables.size(); i++) {
            String value = values.get(i);
            if (value != null && variables.get(i).name().equals(name)) {
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(value);
            }
        }
        return result == null ? List.of() : Collections.unmodifiableList(result);
    }

    @Override
    public boolean hasOffsets() {
        return offsets != null;
    }

    @Override
    public int start(int index) {
        Objects.checkIndex(index, variables.size());
        int[] known = offsets;
        return known == null ? -1 : known[2 * index];
    }

    @Override
    public int end(int index) {
        Objects.checkIndex(index, variables.size());
        int[] known = offsets;
        return known == null ? -1 : known[2 * index + 1];
    }

    @Override
    public UriMatchInfo toUriMatchInfo() {
        List<UriMatchVariable> uriMatchVariables = uriMatchVariables(variables);
        return toUriMatchInfo(uriMatchVariables, DefaultUriMatchInfo.variableMap(uriMatchVariables));
    }

    @Override
    public UriMatchInfo toUriMatchInfo(List<UriMatchVariable> uriMatchVariables, Map<String, UriMatchVariable> uriMatchVariableMap) {
        Map<String, Object> valueMap = LinkedHashMap.newLinkedHashMap(values.size());
        // the names whose first occurrence has no value: allocated only when there is one
        Set<String> absent = null;
        for (int i = 0; i < variables.size(); i++) {
            String name = variables.get(i).name();
            String value = values.get(i);
            if (valueMap.containsKey(name) || absent != null && absent.contains(name)) {
                // the first occurrence of a name, like value(String), even when it has no value
                continue;
            }
            if (value != null) {
                valueMap.put(name, value);
            } else {
                if (absent == null) {
                    absent = new HashSet<>(4);
                }
                absent.add(name);
            }
        }
        return new DefaultUriMatchInfo(path, valueMap, uriMatchVariables, uriMatchVariableMap, this);
    }

    /**
     * @param variables The variables of a template
     * @return The variables as the {@link UriMatchVariable}s of the existing route match API
     */
    static List<UriMatchVariable> uriMatchVariables(List<RouteTemplateVariable> variables) {
        List<UriMatchVariable> result = new ArrayList<>(variables.size());
        for (RouteTemplateVariable variable : variables) {
            result.add(toUriMatchVariable(variable));
        }
        return List.copyOf(result);
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
