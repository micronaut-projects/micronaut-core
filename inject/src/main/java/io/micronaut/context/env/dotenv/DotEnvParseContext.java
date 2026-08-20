/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.env.dotenv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object that holds the state during .env file parsing and resolution.
 * Contains both resolved variables (no dependencies) and unresolved variables (with dependencies).
 */
final class DotEnvParseContext {

    private final Map<String, String> resolvedVars = new HashMap<>();
    private final Map<String, UnresolvedVariable> unresolvedVars = new HashMap<>();

    void addResolvedVariable(String key, String value) {
        resolvedVars.put(key, value);
    }

    void addVariableDependency(String key, String referencedVar) {
        unresolvedVars.computeIfAbsent(key, k -> new UnresolvedVariable())
            .addDependency(referencedVar);
    }

    void setUnresolvedValue(String key, String value) {
        UnresolvedVariable var = unresolvedVars.get(key);
        if (var != null) {
            var.setValue(value);
        }
    }

    boolean hasUnresolvedVariable(String key) {
        return unresolvedVars.containsKey(key);
    }

    Map<String, String> getResolvedVars() {
        return resolvedVars;
    }

    Map<String, UnresolvedVariable> getUnresolvedVars() {
        return unresolvedVars;
    }

    static class UnresolvedVariable {
        private String value;
        private final List<String> dependencies = new ArrayList<>();

        void setValue(String value) {
            this.value = value;
        }

        void addDependency(String varName) {
            dependencies.add(varName);
        }

        String getValue() {
            return value;
        }

        List<String> getDependencies() {
            return dependencies;
        }
    }
}
