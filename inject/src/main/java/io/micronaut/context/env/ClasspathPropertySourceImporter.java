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

import io.micronaut.core.util.ConnectionString;

import java.util.Optional;

/**
 * Imports property sources from classpath locations.
 */
public class ClasspathPropertySourceImporter implements PropertySourceImporter<ClasspathPropertySourceImporter.ClasspathImport> {

    @Override
    public String getPropertySourceKind() {
        return "classpath";
    }

    @Override
    public ClasspathImport newImportDeclaration(ConnectionString connectionString) {
        return new ClasspathImport(connectionString.getPath(), false);
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<ClasspathImport> context) {
        String canonicalLocation = context.getCanonicalLocation();
        ClasspathImport classpathImport = context.importDeclaration();
        return context.importClasspathPropertySource(
            classpathImport.resourcePath(),
            canonicalLocation,
            PropertySource.Origin.of(canonicalLocation),
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
