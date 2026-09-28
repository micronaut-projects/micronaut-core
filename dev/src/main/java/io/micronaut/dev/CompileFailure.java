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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A compilation that failed: the running generation stays as it was and the diagnostics are shown
 * until the next successful compilation.
 *
 * @param kind The language that failed to compile
 * @param diagnostics The compiler's messages, errors first
 * @param at When the compilation failed
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record CompileFailure(SourceKind kind, List<CompileDiagnostic> diagnostics, Instant at) {

    /**
     * Validating constructor.
     *
     * @param kind The kind
     * @param diagnostics The diagnostics
     * @param at The time
     */
    public CompileFailure {
        Objects.requireNonNull(kind, "kind");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(at, "at");
    }

    /**
     * @return The error diagnostics only
     */
    public List<CompileDiagnostic> errors() {
        return diagnostics.stream().filter(d -> d.severity() == CompileDiagnostic.Severity.ERROR).toList();
    }

    /**
     * The failure as the console shows it: one line per diagnostic.
     *
     * @return The text
     */
    public String describe() {
        StringBuilder text = new StringBuilder(kind.name().toLowerCase(java.util.Locale.ROOT)).append(" compilation failed:\n");
        for (CompileDiagnostic diagnostic : diagnostics) {
            text.append("  ").append(diagnostic.severity()).append(' ');
            if (diagnostic.file() != null) {
                text.append(diagnostic.file()).append(':').append(diagnostic.line()).append(": ");
            }
            text.append(diagnostic.message().strip()).append('\n');
        }
        return text.toString();
    }
}
