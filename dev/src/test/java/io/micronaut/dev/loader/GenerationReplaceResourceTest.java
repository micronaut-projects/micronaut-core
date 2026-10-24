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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationReplaceResourceTest {

    @TempDir
    Path project;

    @Test
    void aResourceOfTheSnapshotIsReplacedAndANameOutsideTheRootIsRefusedWhateverItsSeparators() throws Exception {
        Path output = Files.createDirectories(project.resolve("build/resources/pkg"));
        Files.writeString(output.resolve("mod.py"), "one", StandardCharsets.UTF_8);
        // the files a name escaping the snapshot root would reach, on Windows or here
        Path outside = Files.writeString(project.resolve("outside"), "untouched", StandardCharsets.UTF_8);
        try (GenerationClassLoader generation = GenerationClassLoader.snapshot(1, List.of(project.resolve("build/resources")), project.resolve("generations"), null)) {
            Path snapshotRoot = generation.roots().get(0);

            assertTrue(generation.replaceResource("pkg/mod.py", "two".getBytes(StandardCharsets.UTF_8)));
            assertEquals("two", Files.readString(snapshotRoot.resolve("pkg/mod.py")));
            assertFalse(generation.replaceResource("pkg/missing.py", new byte[0]));

            for (String name : List.of(
                "",
                // absolute, on any platform
                "/etc/hosts", outside.toAbsolutePath().toString(),
                // a drive, with either separator, and drive-relative
                "C:\\path\\to\\file", "C:/path/to/file", "C:file",
                // a UNC path
                "\\\\server\\share\\file",
                // parent segments with either separator, which only Windows reads as a separator
                "..\\outside", "pkg\\..\\..\\outside", "pkg/..\\..\\outside", "../outside", "pkg/../../outside")) {
                assertThrows(IllegalArgumentException.class, () -> generation.replaceResource(name, "x".getBytes(StandardCharsets.UTF_8)), name);
            }
            assertEquals("untouched", Files.readString(outside));
        }
    }

    @Test
    void theResolvedTargetMustStayUnderTheNormalizedRoot() {
        Path root = project.resolve("root/./snapshot");
        assertEquals(project.resolve("root/snapshot/pkg/mod.py").toAbsolutePath().normalize(), GenerationClassLoader.resolveUnder(root, "pkg/mod.py"));
        // a name that normalizes outside the root, or to the root itself, or that the file system reads as absolute
        assertThrows(IllegalArgumentException.class, () -> GenerationClassLoader.resolveUnder(root, "pkg/../../outside"));
        assertThrows(IllegalArgumentException.class, () -> GenerationClassLoader.resolveUnder(root, "../snapshot-sibling/file"));
        assertThrows(IllegalArgumentException.class, () -> GenerationClassLoader.resolveUnder(root, "pkg/.."));
        assertThrows(IllegalArgumentException.class, () -> GenerationClassLoader.resolveUnder(root, project.resolve("outside").toAbsolutePath().toString()));
    }
}
