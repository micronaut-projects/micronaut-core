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

import org.jspecify.annotations.NullMarked;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The include and exclude patterns of one registration, compiled once when the registration is made.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@NullMarked
final class WatchFilter {

    private static final String ALL_BELOW = "/**";

    private final boolean recursive;
    private final List<PathMatcher> includes;
    private final List<PathMatcher> excludes;
    /**
     * For each exclude pattern that ends with {@code /**}, the pattern of the directories everything below which it
     * excludes; a pattern of only {@code **} excludes everything, which no directory pattern stands for.
     */
    private final List<PathMatcher> excludedTrees;
    private final boolean excludesAll;

    WatchFilter(boolean recursive, Set<String> includeGlobs, Set<String> excludeGlobs) {
        this.recursive = recursive;
        this.includes = compile(includeGlobs);
        this.excludes = compile(excludeGlobs);
        Set<String> trees = new LinkedHashSet<>();
        boolean all = false;
        for (String glob : excludeGlobs) {
            if (glob.equals("**")) {
                all = true;
            } else if (glob.endsWith(ALL_BELOW) && glob.length() > ALL_BELOW.length()) {
                trees.add(glob.substring(0, glob.length() - ALL_BELOW.length()));
            }
        }
        this.excludedTrees = compile(trees);
        this.excludesAll = all;
    }

    boolean recursive() {
        return recursive;
    }

    /**
     * Whether a path, given relative to the root, passes the include and exclude patterns.
     *
     * @param relativePath The path relative to the registration's root
     * @return True if the path is accepted
     */
    boolean accepts(Path relativePath) {
        if (isExcluded(relativePath)) {
            return false;
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (PathMatcher include : includes) {
            if (include.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a path, given relative to the root, matches an exclude pattern.
     *
     * @param relativePath The path relative to the registration's root
     * @return True if excluded
     */
    boolean isExcluded(Path relativePath) {
        for (PathMatcher exclude : excludes) {
            if (exclude.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a directory, given relative to the root, need not be watched, because an exclude pattern excludes every
     * path below it: {@code build/**} for {@code build}, but not {@code build/*}, which leaves {@code build/sub/a} in.
     *
     * @param relativeDirectory The directory relative to the registration's root
     * @return True if the directory need not be watched
     */
    boolean excludesDirectory(Path relativeDirectory) {
        if (excludesAll) {
            return true;
        }
        for (PathMatcher tree : excludedTrees) {
            if (tree.matches(relativeDirectory)) {
                return true;
            }
        }
        return false;
    }

    private static List<PathMatcher> compile(Set<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>(globs.size());
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        return List.copyOf(matchers);
    }
}
