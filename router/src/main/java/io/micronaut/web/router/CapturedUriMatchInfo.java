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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The match of a route by the parser of a {@link io.micronaut.web.router.spi.RoutePlan}: the
 * captured values of the route's path variables.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class CapturedUriMatchInfo implements UriMatchInfo {
    private final String uri;
    private final List<UriMatchVariable> variables;
    private final Map<String, Object> values;
    private final Map<String, UriMatchVariable> variableMap;

    /**
     * @param uri       The matched path
     * @param variables The variables of the route's template, in order
     * @param captured  The captured raw values of the path variables, in the same order; query
     *                  variables are not captured, as the matcher of the template does not match them
     */
    CapturedUriMatchInfo(String uri, List<UriMatchVariable> variables, String[] captured) {
        this.uri = uri;
        this.variables = variables;
        this.values = LinkedHashMap.newLinkedHashMap(variables.size());
        this.variableMap = LinkedHashMap.newLinkedHashMap(variables.size());
        int next = 0;
        for (UriMatchVariable variable : variables) {
            if (!variable.isQuery() && next < captured.length) {
                values.put(variable.getName(), captured[next++]);
            }
            variableMap.put(variable.getName(), variable);
        }
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return values;
    }

    @Override
    public List<UriMatchVariable> getVariables() {
        return variables;
    }

    @Override
    public Map<String, UriMatchVariable> getVariableMap() {
        return variableMap;
    }
}
