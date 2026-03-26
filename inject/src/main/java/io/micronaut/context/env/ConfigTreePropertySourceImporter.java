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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Imports key/value configuration from a config tree directory.
 */
public final class ConfigTreePropertySourceImporter implements PropertySourceImporter {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigTreePropertySourceImporter.class);

    @Override
    public String getProtocol() {
        return "configtree";
    }

    @Override
    public Optional<PropertySource> importPropertySource(ImportContext context) {
        String canonicalLocation = context.getCanonicalLocation();
        String pathValue = context.getResourcePath();
        Path root = Paths.get(pathValue);
        if (!Files.exists(root) || !Files.isDirectory(root) || !Files.isReadable(root)) {
            return Optional.empty();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream
                .filter(Files::isRegularFile)
                .toList();
            for (Path path : files) {
                if (isHiddenPath(root, path)) {
                    continue;
                }
                Path relative = root.relativize(path);
                String key = relative.toString().replace('\\', '.').replace('/', '.');
                try {
                    values.put(key, Files.readString(path));
                } catch (IOException e) {
                    LOG.warn("Skipping unreadable config tree file [{}]: {}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PropertySource.of(canonicalLocation, values, PropertySource.Origin.of(canonicalLocation)));
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
            return true;
        }
    }
}
