/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.ObjectUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default {@link UriMatchInfo} implementation.
 *
 * @author Denis Stepanov
 * @since 4.6.0
 */
@Internal
final class DefaultUriMatchInfo implements UriMatchInfo {

    private final String uri;
    private final Map<String, Object> variableValues;
    private final List<UriMatchVariable> variables;
    private final Map<String, UriMatchVariable> variableMap;

    /**
     * @param uri            The URI
     * @param variableValues The map of variable names with values
     * @param variables      The variables
     */
    DefaultUriMatchInfo(String uri, Map<String, Object> variableValues, List<UriMatchVariable> variables) {
        this.uri = uri;
        this.variableValues = variableValues;
        this.variables = variables;
        LinkedHashMap<String, UriMatchVariable> vm = CollectionUtils.newLinkedHashMap(variables.size());
        for (UriMatchVariable variable : variables) {
            vm.put(variable.getName(), variable);
        }
        this.variableMap = Collections.unmodifiableMap(vm);
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return variableValues;
    }

    @Override
    public List<UriMatchVariable> getVariables() {
        return Collections.unmodifiableList(variables);
    }

    @Override
    public Map<String, UriMatchVariable> getVariableMap() {
        return variableMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultUriMatchInfo that = (DefaultUriMatchInfo) o;
        return uri.equals(that.uri) && variables.equals(that.variables);
    }

    @Override
    public String toString() {
        return getUri();
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(uri, variableValues);
    }
}
