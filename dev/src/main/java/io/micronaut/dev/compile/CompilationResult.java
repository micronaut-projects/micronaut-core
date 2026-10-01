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
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What a compilation produced.
 *
 * @param status Whether it succeeded, failed, or had nothing to do
 * @param diagnostics The compiler's messages
 * @param compiledSources The sources that were compiled, absolute
 * @param removedOutputs The output files removed for deleted sources, absolute
 * @param duration How long the compilation took
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record CompilationResult(Status status,
                                List<CompileDiagnostic> diagnostics,
                                Set<Path> compiledSources,
                                Set<Path> removedOutputs,
                                Duration duration) {

    /**
     * Validating constructor.
     *
     * @param status The status
     * @param diagnostics The diagnostics
     * @param compiledSources The compiled sources
     * @param removedOutputs The removed outputs
     * @param duration The duration
     */
    public CompilationResult {
        Objects.requireNonNull(status, "status");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        compiledSources = Set.copyOf(Objects.requireNonNull(compiledSources, "compiledSources"));
        removedOutputs = Set.copyOf(Objects.requireNonNull(removedOutputs, "removedOutputs"));
        Objects.requireNonNull(duration, "duration");
    }

    /**
     * A result with nothing to do.
     *
     * @return The result
     */
    public static CompilationResult nothingToDo() {
        return new CompilationResult(Status.NOTHING_TO_DO, List.of(), Set.of(), Set.of(), Duration.ZERO);
    }

    /**
     * @return Whether the compilation succeeded or had nothing to do
     */
    public boolean isSuccess() {
        return status != Status.FAILED;
    }

    /**
     * @return The error diagnostics
     */
    public List<CompileDiagnostic> errors() {
        return diagnostics.stream().filter(CompileDiagnostic::isError).toList();
    }

    /**
     * The outcome of a compilation.
     */
    public enum Status {
        /**
         * The sources compiled and the outputs are in place.
         */
        SUCCESS,
        /**
         * The compilation failed; the outputs are as they were.
         */
        FAILED,
        /**
         * No source needed compiling.
         */
        NOTHING_TO_DO
    }
}
