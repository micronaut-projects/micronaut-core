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
package io.micronaut.context.watch;

import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a resource watch selects: a kind of resource root and globs relative to it, such as
 * {@code of(VIEWS, "**&#47;*.html")}. No glob selects everything of the kind.
 *
 * @param kind The kind of resource root
 * @param includeGlobs The globs, relative to a root of the kind; empty for every file
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record ResourceSelector(ResourceKind kind, Set<String> includeGlobs) {

    /**
     * Validating constructor.
     *
     * @param kind The kind
     * @param includeGlobs The globs
     */
    public ResourceSelector {
        Objects.requireNonNull(kind, "kind");
        includeGlobs = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(includeGlobs, "includeGlobs")));
    }

    /**
     * Selects files of a kind by glob.
     *
     * @param kind The kind
     * @param globs The globs, relative to a root of the kind; none for every file
     * @return The selector
     */
    public static ResourceSelector of(ResourceKind kind, String... globs) {
        return new ResourceSelector(kind, Set.of(globs));
    }

    /**
     * Whether a file under one of the given roots is selected.
     *
     * @param roots The roots of the kind
     * @param file The file, absolute
     * @return True if the file is under a root and matches a glob, or there is no glob
     */
    public boolean matches(List<Path> roots, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        for (Path root : roots) {
            Path base = root.toAbsolutePath().normalize();
            if (absolute.startsWith(base)) {
                if (includeGlobs.isEmpty()) {
                    return true;
                }
                // roots may nest: a file the glob rejects relative to an outer root may match relative to an inner one
                Path relative = base.relativize(absolute);
                for (PathMatcher matcher : matchers()) {
                    if (matcher.matches(relative)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<PathMatcher> matchers() {
        List<PathMatcher> matchers = new ArrayList<>(includeGlobs.size());
        for (String glob : includeGlobs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
            if (glob.startsWith("**/")) {
                // "any depth" includes the root itself, which the glob syntax alone leaves out
                matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob.substring(3)));
            }
        }
        return matchers;
    }
}
