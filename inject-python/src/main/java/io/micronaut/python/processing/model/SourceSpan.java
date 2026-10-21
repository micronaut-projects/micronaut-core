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
package io.micronaut.python.processing.model;

import io.micronaut.core.annotation.Experimental;

import java.util.Objects;

/**
 * The location of a definition in its original Python source.
 *
 * <p>Lines are one-based. Columns are one-based character (not byte) offsets into the line, and
 * the end column is exclusive, following the convention of Python's {@code end_col_offset}. A
 * definition the compiler generated, such as the decorator functions standing in for imported
 * Java annotations, has no span.</p>
 *
 * @param path      The path of the source file, or its name when the source has no path
 * @param line      The one-based line of the first character of the definition
 * @param column    The one-based column of the first character of the definition
 * @param endLine   The one-based line of the last character of the definition
 * @param endColumn The one-based column just past the last character of the definition
 * @since 5.3.0
 */
@Experimental
public record SourceSpan(String path, int line, int column, int endLine, int endColumn) {

    public SourceSpan {
        Objects.requireNonNull(path, "Source path cannot be null");
    }

    /**
     * A span covering a single position.
     *
     * @param path   The source path
     * @param line   The one-based line
     * @param column The one-based column
     * @return The span
     */
    public static SourceSpan at(String path, int line, int column) {
        return new SourceSpan(path, line, column, line, column + 1);
    }

    /**
     * @return The location as {@code path:line:column}
     */
    public String location() {
        return path + ":" + line + ":" + column;
    }

    @Override
    public String toString() {
        return location();
    }
}
