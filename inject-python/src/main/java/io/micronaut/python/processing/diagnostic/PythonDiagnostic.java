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
package io.micronaut.python.processing.diagnostic;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.python.processing.model.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A problem found in a Python source at compile time, located in that source.
 *
 * @param kind        Whether the problem fails the compilation or is only reported
 * @param rule        The stable identifier of the check that found the problem, such as {@code unresolved-import}
 * @param message     The message, without a location
 * @param span        Where the problem is, or {@code null} when it has no position
 * @param suggestions Alternatives the message may offer, such as similarly named members
 * @since 5.3.0
 */
@Experimental
public record PythonDiagnostic(Kind kind,
                               String rule,
                               String message,
                               @Nullable SourceSpan span,
                               List<String> suggestions) {

    public PythonDiagnostic {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /**
     * @param rule    The rule
     * @param message The message
     * @param span    The location, or {@code null}
     * @return An error
     */
    public static PythonDiagnostic error(String rule, String message, @Nullable SourceSpan span) {
        return new PythonDiagnostic(Kind.ERROR, rule, message, span, List.of());
    }

    /**
     * @param rule    The rule
     * @param message The message
     * @param span    The location, or {@code null}
     * @return A warning
     */
    public static PythonDiagnostic warning(String rule, String message, @Nullable SourceSpan span) {
        return new PythonDiagnostic(Kind.WARNING, rule, message, span, List.of());
    }

    /**
     * @param suggestions The suggestions
     * @return A copy of this diagnostic carrying the suggestions
     */
    public PythonDiagnostic withSuggestions(List<String> suggestions) {
        return new PythonDiagnostic(kind, rule, message, span, suggestions);
    }

    /**
     * @return Whether this diagnostic fails the compilation
     */
    public boolean isError() {
        return kind == Kind.ERROR;
    }

    @Override
    public String toString() {
        String prefix = kind.name().toLowerCase() + ": [python:" + rule + "] " + message;
        return span == null ? prefix : prefix + " (" + span.location() + ")";
    }

    /**
     * The severity of a diagnostic.
     */
    public enum Kind {
        /**
         * Fails the compilation.
         */
        ERROR,
        /**
         * Reported without failing the compilation.
         */
        WARNING
    }
}
