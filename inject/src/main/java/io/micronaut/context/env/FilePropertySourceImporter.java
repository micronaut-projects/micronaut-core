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

import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.util.ConnectionString;

import java.util.Optional;

/**
 * Imports property sources from file system locations.
 */
public final class FilePropertySourceImporter implements PropertySourceImporter<FilePropertySourceImporter.FileImport> {

    @Override
    public String getPropertySourceKind() {
        return "file";
    }

    @Override
    public FileImport newImportDeclaration(ConnectionString connectionString) {
        return new FileImport(connectionString.getPath());
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<FileImport> context) {
        String canonicalLocation = context.getCanonicalLocation();
        return context.importPropertySource(
            FileSystemResourceLoader.defaultLoader(),
            context.getResourcePath(),
            canonicalLocation,
            PropertySource.Origin.of(canonicalLocation)
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
