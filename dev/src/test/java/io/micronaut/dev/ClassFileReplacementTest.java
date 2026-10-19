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

import io.micronaut.dev.loader.GenerationClassLoader;
import io.micronaut.dev.loader.TestGenerations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassFileReplacementTest {

    @TempDir
    Path root;

    @Test
    void aClassFileTheSnapshotDoesNotHoldFailsTheReplacementAndTheRollBackRestoresTheOthers() throws IOException {
        Path greeter = Files.createDirectories(root.resolve("app")).resolve("Greeter.class");
        Path other = root.resolve("app/Other.class");
        Files.write(greeter, new byte[] {1});
        Files.write(other, new byte[] {2});
        try (GenerationClassLoader generation = TestGenerations.over(1, List.of(root));
             ClassFileReplacement replacement = new ClassFileReplacement(generation)) {
            replacement.replace("app.Greeter", new byte[] {1}, new byte[] {10});
            replacement.replace("app.Other", new byte[] {2}, new byte[] {20});
            assertArrayEquals(new byte[] {10}, Files.readAllBytes(greeter));

            // no class file of that name: nothing written, and the caller restarts instead of counting a redefinition
            assertThrows(IOException.class, () -> replacement.replace("app.Missing", new byte[] {3}, new byte[] {30}));
            assertFalse(Files.exists(root.resolve("app/Missing.class")));

            replacement.rollBack();
            assertArrayEquals(new byte[] {1}, Files.readAllBytes(greeter));
            assertArrayEquals(new byte[] {2}, Files.readAllBytes(other));
        }
    }

    @Test
    void aClassLoadedWhileTheFilesAreReplacedWaitsForTheRollBackAndIsTheVersionItRestored() throws Exception {
        byte[] before = compile("v1");
        byte[] after = compile("v2");
        Path greeter = Files.createDirectories(root.resolve("snapshot/app")).resolve("Greeter.class");
        Files.write(greeter, before);
        Files.write(root.resolve("snapshot/app/Other.class"), compile("other", "Other"));
        ExecutorService loader = Executors.newSingleThreadExecutor();
        try (GenerationClassLoader generation = TestGenerations.over(1, List.of(root.resolve("snapshot")))) {
            Future<Object> loaded;
            try (ClassFileReplacement replacement = new ClassFileReplacement(generation)) {
                replacement.replace("app.Greeter", before, after);
                // another thread loads the changed class while the redefinition is pending: it waits for the change
                loaded = loader.submit(() -> Class.forName("app.Greeter", true, generation).getMethod("version").invoke(null));
                Thread.sleep(300);
                assertFalse(loaded.isDone());
                // the holder still loads classes, as the JVM does when it verifies a redefinition
                assertEquals("other", Class.forName("app.Other", true, generation).getMethod("version").invoke(null));
                // the JVM refused the redefinition
                replacement.rollBack();
                assertFalse(loaded.isDone());
            }
            // the waiting load reads the file the rollback restored, not the one it found replaced
            assertEquals("v1", loaded.get(30, TimeUnit.SECONDS));
            assertArrayEquals(before, Files.readAllBytes(greeter));
        } finally {
            loader.shutdownNow();
        }
    }

    private byte[] compile(String version) throws IOException {
        return compile(version, "Greeter");
    }

    private byte[] compile(String version, String name) throws IOException {
        Path sources = Files.createDirectories(root.resolve("src-" + version + "/app"));
        Path source = sources.resolve(name + ".java");
        Files.writeString(source, "package app; public class " + name + " { public static String version() { return \"" + version + "\"; } }");
        Path classes = Files.createDirectories(root.resolve("classes-" + version));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, compiler.run(null, null, null, "-d", classes.toString(), source.toString()));
        return Files.readAllBytes(classes.resolve("app/" + name + ".class"));
    }
}
