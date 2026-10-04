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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.dev.compile.SourceRoot;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * How the results of tests discovered from their files, as pytest's, are named: by the file relative to its test
 * source root, and, when another such root holds a file at the same relative path, by the file relative to the
 * directory the roots share, so the two files' results do not merge under one name.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
public final class TestFileNames {

    private static final Set<SourceKind> JVM_KINDS = Set.of(SourceKind.JAVA, SourceKind.KOTLIN, SourceKind.GROOVY);

    private TestFileNames() {
    }

    /**
     * @param kind A kind of source
     * @return Whether its tests are discovered from their files rather than from classes
     */
    public static boolean isFileKind(SourceKind kind) {
        return !JVM_KINDS.contains(kind);
    }

    /**
     * The directory the name of a colliding file is relative to: the deepest directory holding every test source root
     * whose tests are discovered from their files, or, for a root that is that directory itself, the directory holding it.
     *
     * @param roots The test source roots
     * @param root The root holding the file
     * @param real Whether to compare the roots' real paths, through the links they go through
     * @return The directory, or null if the roots share none
     */
    @Nullable
    public static Path base(List<SourceRoot> roots, Path root, boolean real) {
        Path common = commonDirectory(roots, real);
        Path base = common == null || !root.equals(common) ? common : common.getParent();
        return base == null || !root.startsWith(base) ? null : base;
    }

    /**
     * The deepest directory holding every test source root whose tests are discovered from their files.
     *
     * @param roots The test source roots
     * @param real Whether to compare the roots' real paths
     * @return The directory, or null if there is none
     */
    @Nullable
    public static Path commonDirectory(List<SourceRoot> roots, boolean real) {
        Path common = null;
        for (SourceRoot root : roots) {
            if (!isFileKind(root.kind())) {
                continue;
            }
            Path path = root.path().toAbsolutePath().normalize();
            if (real) {
                path = realPath(path);
            }
            if (common == null) {
                common = path;
            } else {
                while (common != null && !path.startsWith(common)) {
                    common = common.getParent();
                }
                if (common == null) {
                    return null;
                }
            }
        }
        return common;
    }

    /**
     * @param path A path
     * @return Its real path, or the path itself if it has none
     */
    public static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    /**
     * @param path A relative path
     * @return It with {@code /} as the separator on every platform
     */
    public static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }
}
