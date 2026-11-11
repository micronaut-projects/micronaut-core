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
        return new DefaultUriMatchInfo(path, valueMap, uriMatchVariables, uriMatchVariableMap);
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
