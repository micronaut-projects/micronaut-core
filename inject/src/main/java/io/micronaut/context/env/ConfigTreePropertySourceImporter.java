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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Imports key/value configuration from a config tree directory.
 */
@SuppressWarnings("java:S2083") // paths are validated against path-traversal attacks: scalar imports via ConnectionString.getCanonicalPath(), structured imports via explicit normalization in newImportDeclaration(ConvertibleValues)
public final class ConfigTreePropertySourceImporter implements PropertySourceImporter<ConfigTreePropertySourceImporter.ConfigTreeImport> {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigTreePropertySourceImporter.class);

    @Override
    public String getProvider() {
        return "configtree";
    }

    @Override
    public ConfigTreeImport newImportDeclaration(ConnectionString connectionString) {
        return new ConfigTreeImport(connectionString.getPath());
    }

    @Override
    public ConfigTreeImport newImportDeclaration(ConvertibleValues<Object> values) {
        String path = values.get(FilePropertySourceImporter.RESOURCE_PATH, String.class)
            .orElseThrow(() -> new ConfigurationException("Config import provider [configtree] requires ['" + FilePropertySourceImporter.RESOURCE_PATH + "']"));
        if (path.isBlank()) {
            throw new ConfigurationException("Config import provider [configtree] requires a non-blank ['" + FilePropertySourceImporter.RESOURCE_PATH + "']");
        }
        // Validate path against traversal attacks, consistent with scalar imports (ConnectionString.getCanonicalPath())
        Path normalized = Paths.get(path).normalize();
        for (Path segment : normalized) {
            if ("..".equals(segment.toString())) {
                throw new ConfigurationException("Parent path segments are not allowed in configtree import path: " + path);
            }
        }
        return new ConfigTreeImport(normalized.toString());
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext<ConfigTreeImport> context) {
        String sourceName = context.connectionString() != null
            ? context.getCanonicalLocation()
            : getProvider() + "://" + context.importDeclaration().path();
        ConfigTreeImport configTreeImport = context.importDeclaration();
        Path root = Paths.get(configTreeImport.path());
        if (!Files.exists(root) || !Files.isDirectory(root) || !Files.isReadable(root)) {
            return Optional.empty();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                if (isHiddenPath(root, path)) {
                    continue;
                }
                readConfigTreeFile(root, path, values);
            }
        } catch (IOException e) {
            LOG.warn("Failed to walk config tree directory [{}]: {}", configTreeImport.path(), e.getMessage());
            return Optional.empty();
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(sourceName, values, PropertySource.Origin.of(sourceName)));
    }

    private static String trimSingleTrailingNewline(String value) {
        if (value.endsWith("\r\n")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static void readConfigTreeFile(Path root, Path path, Map<String, Object> values) {
        Path relative = root.relativize(path);
        String key = relative.toString().replace('\\', '.').replace('/', '.');
        try {
            values.put(key, trimSingleTrailingNewline(Files.readString(path)));
        } catch (IOException e) {
            LOG.warn("Skipping unreadable config tree file [{}]: {}", path, e.getMessage());
        }
    }

    private static boolean isHiddenPath(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        try {
            return Files.isHidden(file);
        } catch (IOException e) {
            LOG.debug("Could not determine hidden status for config tree file [{}], treating as visible: {}", file, e.getMessage());
            return false;
        }
    }

    /**
     * Typed config tree import declaration.
     *
     * @param path The config tree root directory path
     */
    public record ConfigTreeImport(String path) {
    }
}
