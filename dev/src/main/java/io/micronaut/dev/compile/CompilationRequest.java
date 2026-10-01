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
package io.micronaut.dev.compile;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a {@link SourceCompiler} is asked to compile.
 *
 * <p>An incremental request names the sources that changed and the ones that were deleted since
 * the previous compilation; the compiler decides what else must be recompiled. A full request
 * ({@link #isFull()}) names nothing and compiles every source under the roots.</p>
 *
 * @param kind The language
 * @param sourceRoots The roots of this language
 * @param changed The changed or added sources, absolute; empty for a full compilation
 * @param deleted The deleted sources, absolute
 * @param full Whether every source is compiled, whatever changed
 * @param compileClasspath What the sources compile against, not including the output directory
 * @param processorPath The annotation processor path; empty, javac discovers processors on the compile classpath as it does without one, and {@code -proc:none} among the options disables them
 * @param classOutput Where class files and resources the compiler generates go
 * @param generatedSources Where generated sources go
 * @param options The compiler options, as the build passes them
 * @param affectedClasses Top-level classes another language's compilation changed in this batch, whose dependents among these sources are recompiled too
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record CompilationRequest(
    SourceKind kind,
    List<SourceRoot> sourceRoots,
    Set<Path> changed,
    Set<Path> deleted,
    boolean full,
    List<Path> compileClasspath,
    List<Path> processorPath,
    Path classOutput,
    Path generatedSources,
    List<String> options,
    Set<String> affectedClasses) {

    /**
     * Validating constructor.
     *
     * @param kind The language
     * @param sourceRoots The roots
     * @param changed The changed sources
     * @param deleted The deleted sources
     * @param full Whether every source is compiled
     * @param compileClasspath The compile classpath
     * @param processorPath The processor path
     * @param classOutput The class output
     * @param generatedSources The generated sources output
     * @param options The options
     */
    public CompilationRequest {
        Objects.requireNonNull(kind, "kind");
        sourceRoots = List.copyOf(Objects.requireNonNull(sourceRoots, "sourceRoots"));
        changed = Set.copyOf(Objects.requireNonNull(changed, "changed"));
        deleted = Set.copyOf(Objects.requireNonNull(deleted, "deleted"));
        compileClasspath = List.copyOf(Objects.requireNonNull(compileClasspath, "compileClasspath"));
        processorPath = List.copyOf(Objects.requireNonNull(processorPath, "processorPath"));
        classOutput = Objects.requireNonNull(classOutput, "classOutput").toAbsolutePath().normalize();
        generatedSources = Objects.requireNonNull(generatedSources, "generatedSources").toAbsolutePath().normalize();
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        affectedClasses = Set.copyOf(Objects.requireNonNull(affectedClasses, "affectedClasses"));
    }

    /**
     * A request with no classes affected by another language.
     *
     * @param kind The language
     * @param sourceRoots The roots
     * @param changed The changed sources
     * @param deleted The deleted sources
     * @param full Whether full
     * @param compileClasspath The compile classpath
     * @param processorPath The processor path
     * @param classOutput The class output
     * @param generatedSources The generated sources directory
     * @param options The options
     */
    public CompilationRequest(SourceKind kind, List<SourceRoot> sourceRoots, Set<Path> changed, Set<Path> deleted, boolean full, List<Path> compileClasspath, List<Path> processorPath, Path classOutput, Path generatedSources, List<String> options) {
        this(kind, sourceRoots, changed, deleted, full, compileClasspath, processorPath, classOutput, generatedSources, options, Set.of());
    }

    /**
     * This request with the classes another language's compilation changed.
     *
     * @param classes The top-level classes
     * @return The request
     */
    public CompilationRequest withAffectedClasses(Set<String> classes) {
        return new CompilationRequest(kind, sourceRoots, changed, deleted, full, compileClasspath, processorPath, classOutput, generatedSources, options, classes);
    }

    /**
     * @return Whether every source is compiled
     */
    public boolean isFull() {
        return full;
    }

    /**
     * A request that compiles every source under the roots.
     *
     * @return The full request
     */
    public CompilationRequest asFull() {
        return new CompilationRequest(kind, sourceRoots, Set.of(), Set.of(), true, compileClasspath, processorPath, classOutput, generatedSources, options, Set.of());
    }
}
