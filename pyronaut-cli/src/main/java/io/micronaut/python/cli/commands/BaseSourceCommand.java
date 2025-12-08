/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

abstract class BaseSourceCommand extends BaseCommand {
    protected Path resolveSourceDir() {
        try {
            return Path.of(".").toFile().getCanonicalFile().toPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static List<File> asClasspath(Path dependenciesDir) {
        if (Files.isDirectory(dependenciesDir)) {
            try (var walker = Files.walk(dependenciesDir)) {
                return walker.map(Path::toFile)
                    .filter(f -> f.getName().endsWith(".jar"))
                    .toList();
            } catch (IOException ex) {
                return List.of();
            }
        }
        return List.of();
    }
}
