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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import org.graalvm.polyglot.Value;

public final class GraalPyUtil {
    /**
     * Utility method to convert GraalPy Value objects to Java types.
     * This extracts the common type conversion logic used for both annotations and attribute values.
     *
     * @param value the GraalPy Value to convert
     * @return the converted Java object, or the original value if conversion is not possible
     */
    public static Object convertValueToJava(Value value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isNumber()) {
            if (value.fitsInByte()) {
                return value.asByte();
            } else if (value.fitsInShort()) {
                return value.asShort();
            } else if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else if (value.fitsInFloat()) {
                return value.asFloat();
            } else if (value.fitsInDouble()) {
                return value.asDouble();
            } else {
                return value.asString();
            }
        } else if (value.isString()) {
            return value.asString();
        }
        return value;
    }

    /**
     * Resolves a Python type annotation to a Java ClassElement.
     * Attempts to map Python primitive types to equivalent Java primitive types using PrimitiveElement,
     * otherwise falls back to visitor context lookup.
     *
     * @param typeAnnotation the Python type annotation string (e.g., "int", "str", "bool", "float")
     * @param visitorContext the visitor context for class element lookup
     * @return the resolved ClassElement, or Object ClassElement if resolution fails
     */
    public static ClassElement resolvePythonTypeToJava(String typeAnnotation, PythonVisitorContext visitorContext) {
        if (typeAnnotation == null || typeAnnotation.isBlank()) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }

        // Try to map Python primitive types to Java primitives
        switch (typeAnnotation) {
            case "int":
                return PrimitiveElement.INT;
            case "float":
                return PrimitiveElement.DOUBLE;
            case "bool":
                return PrimitiveElement.BOOLEAN;
            case "str":
                return visitorContext.getClassElement(String.class).orElse(ClassElement.of(String.class));
            default:
                // Fall back to visitor context lookup
                return visitorContext.getClassElement(typeAnnotation).orElse(
                    visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class))
                );
        }
    }

    /**
     * Parses a Python docstring to extract the main description.
     * Removes opening/closing quotes and stops at structured sections like Args:, Returns:, etc.
     *
     * @param docstring the raw Python docstring
     * @return the parsed main description, or empty string if docstring is null/empty
     */
    public static String parsePythonDocstring(String docstring) {
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
