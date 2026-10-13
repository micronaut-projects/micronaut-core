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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        try (GenerationClassLoader generation = TestGenerations.over(1, List.of(root))) {
            ClassFileReplacement replacement = new ClassFileReplacement(generation);
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
}
