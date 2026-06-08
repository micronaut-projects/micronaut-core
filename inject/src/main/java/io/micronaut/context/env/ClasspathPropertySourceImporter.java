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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Imports property sources from classpath locations.
 */
public class ClasspathPropertySourceImporter implements PropertySourceImporter<ClasspathPropertySourceImporter.ClasspathImport> {

    @Override
    public String getProvider() {
        return "classpath";
    }

    @Override
    public ClasspathImport newImportDeclaration(ConnectionString connectionString) {
        return new ClasspathImport(connectionString.getPath(), false);
    }

    @Override
    public ClasspathImport newImportDeclaration(ConvertibleValues<Object> values) {
        String resourcePath = values.get(FilePropertySourceImporter.RESOURCE_PATH, String.class)
            .orElseThrow(() -> new ConfigurationException("Config import provider [classpath] requires ['" + FilePropertySourceImporter.RESOURCE_PATH + "']"));
        if (resourcePath.isBlank()) {
            throw new ConfigurationException("Config import provider [classpath] requires a non-blank ['" + FilePropertySourceImporter.RESOURCE_PATH + "']");
        }
        // Validate/normalize resource path consistently with scalar imports (ConfigImportIdentity.canonicalClasspathLocation)
        if (resourcePath.startsWith("/")) {
            throw new ConfigurationException("Absolute classpath imports are not supported for security reasons: " + resourcePath);
        }
        Path normalized = Paths.get(resourcePath).normalize();
        for (Path segment : normalized) {
            if ("..".equals(segment.toString())) {
                throw new ConfigurationException("Parent path segments are not allowed in classpath import path: " + resourcePath);
            }
        }
        return new ClasspathImport(normalized.toString().replace('\\', '/'), false);
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<ClasspathImport> context) {
        String sourceName = context.connectionString() != null
            ? context.getCanonicalLocation()
            : getProvider() + "://" + context.importDeclaration().resourcePath();
        ClasspathImport classpathImport = context.importDeclaration();
        return context.importClasspathPropertySource(
            classpathImport.resourcePath(),
            sourceName,
            PropertySource.Origin.of(sourceName),
            classpathImport.allowMultiple()
        );
    }

    /**
     * Typed classpath import declaration.
     *
     * @param resourcePath The classpath resource path
     * @param allowMultiple Whether multiple classpath resources may be merged
     */
    public record ClasspathImport(String resourcePath, boolean allowMultiple) {
    }
}
