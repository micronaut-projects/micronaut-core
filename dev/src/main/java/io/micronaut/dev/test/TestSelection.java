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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Which tests a run runs.
 *
 * <p>Either every test the runner discovers, or those of the given classes, methods and files. Patterns
 * filter either set further, in the form the build tools use for {@code --tests}: a class name or a
 * class name followed by a method name, each of which may hold {@code *} wildcards, such as
 * {@code com.example.*Test} or {@code BookTest.saves*}.</p>
 *
 * @param everything Whether every discovered test runs, ignoring the classes, methods and files
 * @param classes The test classes, by binary name
 * @param methods The test methods, as {@code className#methodName}
 * @param files The test files, for runners that select by file, such as pytest
 * @param patterns The {@code --tests} patterns that filter what is selected; empty to keep all of it
 * @param description Why these tests were selected, such as {@code affected} or {@code all}, for the reports
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestSelection(boolean everything, Set<String> classes, Set<String> methods, Set<Path> files, List<String> patterns, String description) {

    /**
     * Validating constructor.
     *
     * @param everything Whether everything runs
     * @param classes The classes
     * @param methods The methods
     * @param files The files
     * @param patterns The patterns
     * @param description The description
     */
    public TestSelection {
        classes = Set.copyOf(Objects.requireNonNull(classes, "classes"));
        methods = Set.copyOf(Objects.requireNonNull(methods, "methods"));
        files = Set.copyOf(Objects.requireNonNull(files, "files"));
        patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
        Objects.requireNonNull(description, "description");
    }

    /**
     * @return Every test the runner discovers
     */
    public static TestSelection all() {
        return new TestSelection(true, Set.of(), Set.of(), Set.of(), List.of(), "all");
    }

    /**
     * The tests of the given classes.
     *
     * @param classes The classes, by binary name
     * @param description Why they were selected
     * @return The selection
     */
    public static TestSelection ofClasses(Set<String> classes, String description) {
        return new TestSelection(false, classes, Set.of(), Set.of(), List.of(), description);
    }

    /**
     * The same selection, filtered by patterns.
     *
     * @param patterns The {@code --tests} patterns
     * @return The selection
     */
    public TestSelection withPatterns(List<String> patterns) {
        return new TestSelection(everything, classes, methods, files, patterns, description);
    }

    /**
     * @return Whether nothing is selected: not every test, and no class, method or file
     */
    public boolean isEmpty() {
        return !everything && classes.isEmpty() && methods.isEmpty() && files.isEmpty();
    }
}
