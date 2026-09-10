/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.Internal;

/**
 * Renders a Python docstring as element documentation.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonDocstrings {

    private PythonDocstrings() {
    }

    /**
     * Parses a Python docstring to extract the main description.
     * Removes opening/closing quotes and stops at structured sections like Args:, Returns:, etc.
     *
     * @param docstring the raw Python docstring
     * @return the parsed main description, or empty string if docstring is null/empty
     */
    public static String parse(String docstring) {
        if (docstring == null || docstring.trim().isEmpty()) {
            return "";
        }

        String[] lines = docstring.split("\\n");
        StringBuilder result = new StringBuilder();

        // Skip the first line if it's just the opening quotes or empty
        int startIndex = 0;
        if (lines.length > 0 && (lines[0].trim().isEmpty() || lines[0].trim().startsWith("\"\"\"") || lines[0].trim().startsWith("'''"))) {
            startIndex = 1;
        }

        // Process lines until we hit structured sections
        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i];

            // Stop at common section headers (case-insensitive)
            String trimmed = line.trim().toLowerCase();
            if (trimmed.startsWith("args:") || trimmed.startsWith("arguments:") ||
                trimmed.startsWith("parameters:") || trimmed.startsWith("param:") ||
                trimmed.startsWith("returns:") || trimmed.startsWith("return:") ||
                trimmed.startsWith("raises:") || trimmed.startsWith("exceptions:") ||
                trimmed.startsWith("note:") || trimmed.startsWith("notes:") ||
                trimmed.startsWith("example:") || trimmed.startsWith("examples:") ||
                trimmed.startsWith("see also:")) {
                break;
            }

            // Stop at closing docstring markers
            if (line.trim().endsWith("\"\"\"") || line.trim().endsWith("'''")) {
                line = line.replaceAll("\"\"\"$", "").replaceAll("'''$", "");
            }

            result.append(line);
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString().trim();
    }
}
