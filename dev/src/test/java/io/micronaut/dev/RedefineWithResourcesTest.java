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
import io.micronaut.dev.manifest.DevReloadStrategy;
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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change of method bodies and resources together, as an edit of a statically compiled Python function makes: the JVM
 * redefines the classes and a reloader of the application patches the resources, in the running generation, or the
 * change restarts.
 */
class RedefineWithResourcesTest {

    @TempDir
    Path project;

    @Test
    void bodiesAndResourcesAreAppliedTogetherInTheRunningGeneration() throws Exception {
        writeOutput("one", "");
        Files.writeString(data(), "one");
        TextReloader reloader = new TextReloader();
        DevRuntime runtime = runtime(manifest(""), reloader);
        try {
            ApplicationContext first = runtime.start(new String[0]);
            assertEquals(DevReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            Class<?> greeter = runtime.currentGeneration().loadClass("app.Greeter");
            assertEquals("one", greet(greeter));

            writeOutput("two", "");
            Files.writeString(data(), "two");
            runtime.reload();

            assertEquals(1, runtime.generation());
            assertEquals(1, runtime.redefinitions());
            assertEquals(1, runtime.inPlacePatches());
            assertSame(first, runtime.context().orElseThrow());
            assertEquals("two", greet(greeter));
            // the reloader read the new contents through the generation's loader
            assertEquals(List.of("app/data.txt=two"), reloader.read);

            // and again: the generation's snapshot holds what the last change left
            writeOutput("three", "");
            Files.writeString(data(), "three");
            runtime.reload();

            assertEquals(1, runtime.generation());
            assertEquals(2, runtime.redefinitions());
            assertEquals("three", greet(greeter));
            assertEquals(List.of("app/data.txt=two", "app/data.txt=three"), reloader.read);
        } finally {
            runtime.close();
        }
    }

    @Test
    void aStructuralChangeRestartsWithoutReachingTheReloader() throws Exception {
        writeOutput("one", "");
        Files.writeString(data(), "one");
        TextReloader reloader = new TextReloader();
        DevRuntime runtime = runtime(manifest(""), reloader);
        try {
            runtime.start(new String[0]);
            Class<?> greeter = runtime.currentGeneration().loadClass("app.Greeter");

            writeOutput("two", "public static int added() { return 1; }");
            Files.writeString(data(), "two");
            runtime.reload();

            runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertEquals(0, runtime.redefinitions());
            assertEquals(0, runtime.inPlacePatches());
            assertTrue(reloader.read.isEmpty());
            // nor did the retired generation's class see the new body
            assertEquals("one", greet(greeter));
            assertEquals("two", greet(runtime.currentGeneration().loadClass("app.Greeter")));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aResourceNoReloaderTakesRestartsAndLeavesTheRunningClassAlone() throws Exception {
        writeOutput("one", "");
        Path other = data().resolveSibling("other.txt");
        Files.writeString(other, "one");
        DevRuntime runtime = runtime(manifest(""), new TextReloader());
        try {
            runtime.start(new String[0]);
            Class<?> greeter = runtime.currentGeneration().loadClass("app.Greeter");

            writeOutput("two", "");
            Files.writeString(other, "two");
            runtime.reload();

            runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertEquals(0, runtime.redefinitions());
            assertEquals("one", greet(greeter));
            assertEquals("two", greet(runtime.currentGeneration().loadClass("app.Greeter")));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aReloaderThatFailsRestarts() throws Exception {
        writeOutput("one", "");
        Path broken = data().resolveSibling("broken.txt");
        Files.writeString(broken, "one");
        DevRuntime runtime = runtime(manifest(""), new TextReloader());
        try {
            runtime.start(new String[0]);
            runtime.currentGeneration().loadClass("app.Greeter");

            writeOutput("two", "");
            Files.writeString(broken, "two");
            runtime.reload();

            runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertEquals(0, runtime.redefinitions());
            assertEquals(0, runtime.inPlacePatches());
            assertEquals("two", greet(runtime.currentGeneration().loadClass("app.Greeter")));
        } finally {
            runtime.close();
        }
    }

    @Test
    void patchingInPlaceOffRestarts() throws Exception {
        assertRestarts("micronaut.dev.patch-in-place=false\n");
    }

    @Test
    void theRestartStrategyRestarts() throws Exception {
        assertRestarts("micronaut.dev.strategy=restart\n");
    }

    private void assertRestarts(String extra) throws Exception {
        writeOutput("one", "");
        Files.writeString(data(), "one");
        TextReloader reloader = new TextReloader();
        DevRuntime runtime = runtime(manifest(extra), reloader);
        try {
            runtime.start(new String[0]);

            writeOutput("two", "");
            Files.writeString(data(), "two");
            runtime.reload();

            runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertEquals(0, runtime.redefinitions());
            assertEquals(0, runtime.inPlacePatches());
            assertTrue(reloader.read.isEmpty());
        } finally {
            runtime.close();
        }
    }

    private Path data() throws IOException {
        return Files.createDirectories(project.resolve("build/classes/app")).resolve("data.txt");
    }

    private void writeOutput(String greeting, String members) throws IOException {
        Path source = Files.createDirectories(project.resolve("src/app")).resolve("Greeter.java");
        Files.writeString(source, """
            package app;
            public class Greeter {
                public static String greet() {
                    return "%s";
                }
                %s
            }
            """.formatted(greeting, members));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, "-d", project.resolve("build/classes").toString(), source.toString()));
    }

    private static String greet(Class<?> greeter) throws Exception {
        return (String) greeter.getMethod("greet").invoke(null);
    }

    private DevRuntime runtime(DevManifest manifest, TextReloader reloader) {
        DevClassLoader loader = new DevClassLoader(RedefineWithResourcesTest.class.getClassLoader(), manifest.reloadableRoots(), project.resolve("build/generations"));
        return new DevRuntime(manifest, loader, (classLoader, mainClass, args) -> {
            reloader.loader = classLoader;
            ApplicationContext.builder()
                .classLoader(classLoader)
                .properties(Map.of("spec.name", "RedefineWithResourcesTest"))
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
