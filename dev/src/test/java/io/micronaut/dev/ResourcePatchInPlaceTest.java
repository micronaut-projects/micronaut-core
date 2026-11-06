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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.InPlaceResourceReloader;
import io.micronaut.dev.loader.DevClassLoader;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A change of resources alone, patched into the running generation by a reloader of the application, without the
 * Python runtime whose reloader patches its modules this way.
 */
class ResourcePatchInPlaceTest {

    @TempDir
    Path project;

    @Test
    void aResourceAReloaderTakesIsPatchedInAndOneItRefusesRestarts() throws Exception {
        Path classes = Files.createDirectories(project.resolve("build/classes/app"));
        Files.writeString(classes.resolve("data.txt"), "one");
        Files.writeString(classes.resolve("other.txt"), "one");
        Files.writeString(classes.resolve("broken.txt"), "one");
        TextReloader reloader = new TextReloader();
        DevRuntime runtime = runtime(manifest(""), reloader);
        try {
            ApplicationContext first = runtime.start(new String[0]);

            Files.writeString(classes.resolve("data.txt"), "two");
            runtime.reload();
            assertEquals(1, runtime.generation());
            assertEquals(1, runtime.inPlacePatches());
            assertSame(first, runtime.context().orElseThrow());
            // the reloader read the new contents through the generation's loader
            assertEquals(List.of("app/data.txt=two"), reloader.read);

            // a reloader that throws refuses the change: the application restarts
            Files.writeString(classes.resolve("broken.txt"), "two");
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertEquals(1, runtime.inPlacePatches());

            // a resource no reloader takes restarts too
            Files.writeString(classes.resolve("other.txt"), "two");
            runtime.reload();
            runtime.awaitGeneration(3, Duration.ofMinutes(1));
            assertEquals(1, runtime.inPlacePatches());
            assertEquals(false, second.isRunning());
        } finally {
            runtime.close();
        }
    }

    @Test
    void patchingInPlaceCanBeTurnedOff() throws Exception {
        Path classes = Files.createDirectories(project.resolve("build/classes/app"));
        Files.writeString(classes.resolve("data.txt"), "one");
        DevRuntime runtime = runtime(manifest("micronaut.dev.patch-in-place=false\n"), new TextReloader());
        try {
            runtime.start(new String[0]);
            Files.writeString(classes.resolve("data.txt"), "two");
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertEquals(0, runtime.inPlacePatches());
        } finally {
            runtime.close();
        }
    }

    private DevRuntime runtime(DevManifest manifest, TextReloader reloader) {
        DevClassLoader loader = new DevClassLoader(ResourcePatchInPlaceTest.class.getClassLoader(), manifest.reloadableRoots(), project.resolve("build/generations"));
        return new DevRuntime(manifest, loader, (classLoader, mainClass, args) -> {
            reloader.loader = classLoader;
            ApplicationContext.builder()
                .classLoader(classLoader)
                .properties(Map.of("spec.name", "ResourcePatchInPlaceTest"))
                .singletons(reloader)
                .start();
        }, Map.of());
    }

    private DevManifest manifest(String extra) throws IOException {
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            """ + extra);
        return DevManifest.load(manifestFile);
    }

    /**
     * Takes the text resources but {@code app/other.txt}, and fails for {@code app/broken.txt}.
     */
    static final class TextReloader implements InPlaceResourceReloader {
        final List<String> read = new ArrayList<>();
        volatile ClassLoader loader;

        @Override
        public boolean canReload(Set<String> changedResources, Set<String> removedResources) {
            return removedResources.isEmpty() && !changedResources.contains("app/other.txt");
        }

        @Override
        public Result reload(Set<String> changedResources) throws IOException {
            if (changedResources.contains("app/broken.txt")) {
                throw new IOException("cannot apply");
            }
            for (String resource : changedResources) {
                try (InputStream in = loader.getResourceAsStream(resource)) {
                    read.add(resource + "=" + new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            return new Result(changedResources.size(), "text file(s)");
        }
    }
}
