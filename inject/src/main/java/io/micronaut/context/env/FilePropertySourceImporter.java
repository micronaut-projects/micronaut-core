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
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.util.ConnectionString;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Imports property sources from file system locations.
 */
public final class FilePropertySourceImporter implements PropertySourceImporter<FilePropertySourceImporter.FileImport> {

    static final String RESOURCE_PATH = "resource-path";

    @Override
    public String getProvider() {
        return "file";
    }

    @Override
    public FileImport newImportDeclaration(ConnectionString connectionString) {
        return new FileImport(connectionString.getPath());
    }

    @Override
    public FileImport newImportDeclaration(ConvertibleValues<Object> values) {
        String resourcePath = values.get(RESOURCE_PATH, String.class)
            .orElseThrow(() -> new ConfigurationException("Config import provider [file] requires ['" + RESOURCE_PATH + "']"));
        if (resourcePath.isBlank()) {
            throw new ConfigurationException("Config import provider [file] requires a non-blank ['" + RESOURCE_PATH + "']");
        }
        // Validate path against traversal attacks, consistent with scalar imports (ConnectionString.getCanonicalPath())
        Path normalized = Paths.get(resourcePath).normalize();
        for (Path segment : normalized) {
            if ("..".equals(segment.toString())) {
                throw new ConfigurationException("Parent path segments are not allowed in file import path: " + resourcePath);
            }
        }
        return new FileImport(normalized.toString());
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<FileImport> context) {
        String sourceName = context.connectionString() != null
            ? context.getCanonicalLocation()
            : "file://" + context.importDeclaration().resourcePath();
        String resourcePath = context.connectionString() != null
            ? context.getResourcePath()
            : context.importDeclaration().resourcePath();
        return context.importPropertySource(
            FileSystemResourceLoader.defaultLoader(),
            resourcePath,
            sourceName,
            PropertySource.Origin.of(sourceName)
        );
    }

    /**
     * Typed file import declaration.
     *
     * @param resourcePath The file resource path
     */
    public record FileImport(String resourcePath) {
    }
}
