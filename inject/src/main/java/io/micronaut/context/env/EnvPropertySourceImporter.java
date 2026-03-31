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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;

import java.util.Map;
import java.util.Optional;

/**
 * Imports inline configuration stored in an environment variable.
 */
public final class EnvPropertySourceImporter implements PropertySourceImporter<EnvPropertySourceImporter.EnvImport> {

    private static final String VARIABLE = "variable";
    private static final String EXTENSION = "extension";

    @Override
    public String getProvider() {
        return "env";
    }

    @Override
    public EnvImport newImportDeclaration(ConnectionString connectionString) {
        String variableName = connectionString.getPath();
        String extension = connectionString.getOptions().getOrDefault(EXTENSION, connectionString.getExtension().orElse("properties"));
        return new EnvImport(variableName, extension, Map.copyOf(connectionString.getOptions()));
    }

    @Override
    public EnvImport newImportDeclaration(ConvertibleValues<Object> values) {
        String variableName = values.get(VARIABLE, String.class)
            .orElseThrow(() -> new ConfigurationException("Config import provider [env] requires ['" + VARIABLE + "']"));
        if (variableName.isBlank()) {
            throw new ConfigurationException("Config import provider [env] requires a non-blank ['" + VARIABLE + "']");
        }
        String extension = values.get(EXTENSION, String.class).orElse("properties");
        return new EnvImport(variableName, extension, values.asMap());
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<EnvImport> context) {
        EnvImport envImport = context.importDeclaration();
        String value = CachedEnvironment.getenv(envImport.variableName());
        if (value == null) {
            return Optional.empty();
        }
        String sourceName = "env:" + envImport.variableName();
        return context.importPropertySource(value, sourceName, envImport.extension(), PropertySource.Origin.of(sourceName));
    }

    /**
     * Typed environment import declaration.
     *
     * @param variableName The environment variable name
     * @param extension The property source format extension
     * @param options Parsed connection string options
     */
    public record EnvImport(String variableName, String extension, Map<String, Object> options) {
    }
}
