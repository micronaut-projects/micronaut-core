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

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves variable references in .env values using depth-first search.
 * Handles system environment variable fallback and circular dependency detection.
 */
final class DotEnvVariableResolver {

    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private Logger log;

    DotEnvVariableResolver() {}

    DotEnvVariableResolver(Logger log) { this.log = log; }

    /**
     * Resolves all variables in the parse context.
     * Uses DFS to handle chained variable references.
     *
     * @param context the parse context containing resolved and unresolved variables
     */
    void resolveVariables(DotEnvParseContext context) {
        Map<String, String> resolvedVars = context.getResolvedVars();
        Map<String, DotEnvParseContext.UnresolvedVariable> unresolvedVars = context.getUnresolvedVars();
        Map<String, Boolean> checked = new HashMap<>();

        // Create a copy to avoid concurrent modification
        Map<String, DotEnvParseContext.UnresolvedVariable> unresolvedCopy = new HashMap<>(unresolvedVars);

        for (Map.Entry<String, DotEnvParseContext.UnresolvedVariable> entry : unresolvedCopy.entrySet()) {
            String key = entry.getKey();
            DotEnvParseContext.UnresolvedVariable var = entry.getValue();
            resolveVariable(key, var, checked, resolvedVars, unresolvedVars);
        }

        // Clean up escaped dollar signs
        for (Map.Entry<String, String> entry : resolvedVars.entrySet()) {
            String value = entry.getValue();
            resolvedVars.put(entry.getKey(), value.replace("\\$", "$"));
        }
    }

    private void resolveVariable(String key, DotEnvParseContext.UnresolvedVariable var,
                                 Map<String, Boolean> checked,
                                 Map<String, String> resolvedVars,
                                 Map<String, DotEnvParseContext.UnresolvedVariable> unresolvedVars) {
        String value = var.getValue();

        for (String dependency : var.getDependencies()) {
            String processedKey = normalizeKey(dependency);
            String toReplace = "${" + dependency + "}";

            if (!checked.containsKey(dependency)) {
                checked.put(dependency, true);

                if (resolvedVars.containsKey(processedKey)) {
                    // Variable already resolved
                    value = value.replace(toReplace, resolvedVars.get(processedKey));
                } else if (unresolvedVars.containsKey(processedKey)) {
                    // Recursively resolve dependency
                    resolveVariable(processedKey, unresolvedVars.get(processedKey), checked, resolvedVars, unresolvedVars);
                    value = value.replace(toReplace, resolvedVars.get(processedKey));
                } else {
                    // Check system environment as fallback
                    String envValue = System.getenv(dependency);
                    value = value.replace(toReplace, envValue != null ? envValue : "");
                }
            } else {
                if (log != null) {
                    log.warn("Circular dependency detected for variable '{}', replacing with empty string", dependency);
                }
                value = value.replace(toReplace, "");
            }
        }

        resolvedVars.put(key, value);
        unresolvedVars.remove(key);
    }

    private String normalizeKey(String key) {
        if (!key.isEmpty() && !VALID_KEY_PATTERN.matcher(key).matches()) { return ""; }
        return key.toLowerCase().replace('_', '.');
    }
}
