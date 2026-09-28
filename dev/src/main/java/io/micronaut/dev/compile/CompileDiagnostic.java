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
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One message a compiler produced.
 *
 * @param severity Whether it is an error, a warning or a note
 * @param message The message
 * @param file The file it concerns, if any
 * @param line The line in that file, 1-based, or 0 when unknown
 * @param column The column in that line, 1-based, or 0 when unknown
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record CompileDiagnostic(Severity severity, String message, @Nullable Path file, long line, long column) {

    /**
     * Validating constructor.
     *
     * @param severity The severity
     * @param message The message
     * @param file The file
     * @param line The line
     * @param column The column
     */
    public CompileDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    /**
     * @return Whether this diagnostic fails the compilation
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(severity.name().toLowerCase(java.util.Locale.ROOT));
        if (file != null) {
            text.append(": ").append(file);
            if (line > 0) {
                text.append(':').append(line);
                if (column > 0) {
                    text.append(':').append(column);
                }
            }
        }
        return text.append(": ").append(message).toString();
    }

    /**
     * The severity of a diagnostic.
     */
    public enum Severity {
        /**
         * The compilation failed.
         */
        ERROR,
        /**
         * Something worth reading.
         */
        WARNING,
        /**
         * Information.
         */
        NOTE
    }
}
