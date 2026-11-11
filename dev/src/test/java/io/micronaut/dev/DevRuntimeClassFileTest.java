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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevRuntimeClassFileTest {

    @TempDir
    Path directory;

    @Test
    void theClassFileOfTheFirstRootThatHasOne() throws Exception {
        Path first = Files.createDirectories(directory.resolve("first/app"));
        Path second = Files.createDirectories(directory.resolve("second/app"));
        // a directory of the class file's name is skipped
        Files.createDirectories(first.resolve("Greeter.class"));
        Files.write(second.resolve("Greeter.class"), new byte[] {1, 2});
        List<Path> roots = List.of(directory.resolve("first"), directory.resolve("second"));
        assertArrayEquals(new byte[] {1, 2}, InPlaceUpdates.classFile(roots, "app.Greeter"));
        assertNull(InPlaceUpdates.classFile(roots, "app.Missing"));
    }
}
