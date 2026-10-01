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
package io.micronaut.scheduling.io.watch;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Options of a {@link FileWatcher} registration.
 *
 * @param recursive Whether directories below the root are watched too
 * @param includeGlobs Glob patterns, relative to the root, a changed path must match; empty means every path
 * @param excludeGlobs Glob patterns, relative to the root, a changed path must not match
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record WatchOptions(boolean recursive, Set<String> includeGlobs, Set<String> excludeGlobs) {

    /**
     * Recursive, no include or exclude patterns.
     */
    public static final WatchOptions DEFAULT = new WatchOptions(true, Set.of(), Set.of());

    /**
     * Validating constructor.
     *
     * @param recursive Whether directories below the root are watched too
     * @param includeGlobs Glob patterns a changed path must match; empty means every path
     * @param excludeGlobs Glob patterns a changed path must not match
     */
    public WatchOptions {
        Objects.requireNonNull(includeGlobs, "includeGlobs");
        Objects.requireNonNull(excludeGlobs, "excludeGlobs");
        includeGlobs = Set.copyOf(includeGlobs);
        excludeGlobs = Set.copyOf(excludeGlobs);
    }

    /**
     * Options that watch only the root directory itself.
     *
     * @return The options
     */
    public static WatchOptions nonRecursive() {
        return new WatchOptions(false, Set.of(), Set.of());
    }

    /**
     * Copies these options with the given include patterns.
     *
     * @param globs Glob patterns relative to the root, for example {@code **&#47;*.html}
     * @return The options
     */
    public WatchOptions including(String... globs) {
        return new WatchOptions(recursive, Set.of(globs), excludeGlobs);
    }

    /**
     * Copies these options with the given exclude patterns.
     *
     * @param globs Glob patterns relative to the root, for example {@code build/**}
     * @return The options
     */
    public WatchOptions excluding(String... globs) {
        return new WatchOptions(recursive, includeGlobs, Set.of(globs));
    }

    /**
     * Whether a path, given relative to the root, passes the include and exclude patterns.
     *
     * @param relativePath The path relative to the registration's root
     * @return True if the path is accepted
     */
    public boolean accepts(Path relativePath) {
        if (excludes(relativePath)) {
            return false;
        }
        if (includeGlobs.isEmpty()) {
            return true;
        }
        for (PathMatcher include : matchers(includeGlobs)) {
            if (include.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a path, given relative to the root, matches an exclude pattern. A directory that matches
     * is not watched at all, so changes below it are never reported.
     *
     * @param relativePath The path relative to the registration's root
     * @return True if excluded
     */
    public boolean excludes(Path relativePath) {
        for (PathMatcher exclude : matchers(excludeGlobs)) {
            if (exclude.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a directory, given relative to the root, is excluded as a whole: either the directory
     * itself matches an exclude pattern, or every entry below it would (as with {@code build/**}).
     *
     * @param relativeDirectory The directory relative to the registration's root
     * @return True if the directory need not be watched
     */
    public boolean excludesDirectory(Path relativeDirectory) {
        return excludes(relativeDirectory) || excludes(relativeDirectory.resolve("*"));
    }

    private static List<PathMatcher> matchers(Set<String> globs) {
        return globs.stream().map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob)).toList();
    }
}
