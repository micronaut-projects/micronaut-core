/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev;

import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Runtimes for the tests of other packages: created, never started.
 */
public final class DevRuntimes {

    private DevRuntimes() {
    }

    /**
     * @param project The project directory
     * @return A runtime of the project that was not started, whose launcher would start nothing
     * @throws IOException When the manifest cannot be written
     */
    public static DevRuntime unstarted(Path project) throws IOException {
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            """);
        DevManifest manifest = DevManifest.load(manifestFile);
        DevClassLoader loader = new DevClassLoader(DevRuntimes.class.getClassLoader(), manifest.reloadableRoots(), project.resolve("build/generations"));
        return new DevRuntime(manifest, loader, (classLoader, mainClass, args) -> { }, Map.of());
    }
}
