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
package io.micronaut.dev.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationClassLoaderTest {

    @TempDir
    Path directory;

    @Test
    void onlyAClassFileOfTheSnapshotIsReplaced() throws Exception {
        Path empty = Files.createDirectories(directory.resolve("empty"));
        Path classes = Files.createDirectories(directory.resolve("classes/app"));
        Files.write(classes.resolve("Greeter.class"), new byte[] {1});
        Files.createDirectories(empty.resolve("app/Greeter.class"));
        GenerationClassLoader loader = new GenerationClassLoader(1, List.of(empty, directory.resolve("classes")), null);

        // a directory of the class file's name in the first root is no class file
        assertTrue(loader.replaceClassFile("app.Greeter", new byte[] {2}));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(classes.resolve("Greeter.class")));
        assertFalse(loader.replaceClassFile("app.Missing", new byte[] {3}));
        assertFalse(Files.exists(classes.resolve("Missing.class")));
    }
}
