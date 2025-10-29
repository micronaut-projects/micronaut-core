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

import org.graalvm.polyglot.Value;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

/**
 * Utility class for GraalPy integration, providing type conversion and resolution utilities
 * for Python AST processing within Micronaut.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
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
            // Handle single character strings -> char conversion
            String strValue = value.asString();
            if (strValue.length() == 1) {
                return strValue.charAt(0);
            }
            return strValue;
        } else if (value.isMetaObject()) {
            // Handle Python class references
            try {
                if (value.canInvokeMember("__name__")) {
                    Value nameValue = value.invokeMember("__name__");
                    String className = nameValue.asString();
                    // Map Python builtin types to Java types
                    switch (className) {
                        case "str":
                            return String.class;
                        case "int":
                            return Integer.class;
                        case "float":
                            return Double.class;
                        case "bool":
                            return Boolean.class;
                        default:
                            // Try to find the class by name
                            try {
                                return Class.forName(className);
                            } catch (ClassNotFoundException e) {
                                return value;
                            }
                    }
                }
            } catch (Exception e) {
                // Fall back to original value
                return value;
            }
        } else if (value.hasIterator()) {
            // Handle iterable values (like Python lists and arrays) -> typed arrays
            try {
                // Try array access first (works for both arrays and lists in some cases)
                long size = -1;
                try {
                    size = value.getArraySize();
                } catch (Exception e) {
                    // Not an array, try to get size another way
                    if (value.canInvokeMember("__len__")) {
                        Value length = value.invokeMember("__len__");
                        size = length.asLong();
                    }
                }

                if (size > 0) {
                    // Use array element access
                    Value firstElement = value.getArrayElement(0);
                    if (firstElement != null) {
                        // Use first element to determine array type
                        Object convertedFirst = convertValueToJava(firstElement);
                        Class<?> componentType = getComponentType(convertedFirst);

                        // Convert all elements
                        java.util.List<Object> elements = new java.util.ArrayList<>();
                        elements.add(convertedFirst);

                        for (long i = 1; i < size; i++) {
                            Value nextElement = value.getArrayElement(i);
                            if (nextElement != null) {
                                elements.add(convertValueToJava(nextElement));
                            }
                        }

                        // Create typed array
                        return createTypedArray(componentType, elements);
                    }
                }
                // Empty iterable
                return null;
            } catch (Exception e) {
                // Fall back to original value if array conversion fails
                return value;
            }
        }
        return value;
    }

    /**
     * Get the component type for array creation based on the first element.
     */
    private static Class<?> getComponentType(Object firstElement) {
        if (firstElement instanceof Boolean) {
            return boolean.class;
        } else if (firstElement instanceof Byte) {
            return byte.class;
        } else if (firstElement instanceof Character) {
            return char.class;
        } else if (firstElement instanceof Short) {
            return short.class;
        } else if (firstElement instanceof Integer) {
            return int.class;
        } else if (firstElement instanceof Long) {
            return long.class;
        } else if (firstElement instanceof Float) {
            return float.class;
        } else if (firstElement instanceof Double) {
            return double.class;
        } else if (firstElement instanceof String) {
            return String.class;
        } else if (firstElement instanceof Class) {
            return Class.class;
        } else {
            return Object.class;
        }
    }

    /**
     * Create a typed array from the component type and element list.
     */
    private static Object createTypedArray(Class<?> componentType, java.util.List<Object> elements) {
        if (componentType == boolean.class) {
            boolean[] array = new boolean[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = (Boolean) elements.get(i);
            }
            return array;
        } else if (componentType == byte.class) {
            byte[] array = new byte[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).byteValue();
            }
            return array;
        } else if (componentType == char.class) {
            char[] array = new char[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = (Character) elements.get(i);
            }
            return array;
        } else if (componentType == short.class) {
            short[] array = new short[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).shortValue();
            }
            return array;
        } else if (componentType == int.class) {
            int[] array = new int[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).intValue();
            }
            return array;
        } else if (componentType == long.class) {
            long[] array = new long[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).longValue();
            }
            return array;
        } else if (componentType == float.class) {
            float[] array = new float[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).floatValue();
            }
            return array;
        } else if (componentType == double.class) {
            double[] array = new double[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                array[i] = ((Number) elements.get(i)).doubleValue();
            }
            return array;
        } else if (componentType == String.class) {
            return elements.toArray(new String[0]);
        } else if (componentType == Class.class) {
            return elements.toArray(new Class[0]);
        } else {
            return elements.toArray();
        }
    }

    /**
     * Resolves a Python type annotation to a Java ClassElement.
     * Attempts to map Python primitive types to equivalent Java primitive types using PrimitiveElement,
     * otherwise falls back to visitor context lookup.
     *
     * @param typeAnnotation the Python type annotation string (e.g., "int", "str", "bool", "float", "Annotated[float, Gt(0)]")
     * @param visitorContext the visitor context for class element lookup
     * @return the resolved ClassElement, or Object ClassElement if resolution fails
     */
    public static ClassElement resolvePythonTypeToJava(String typeAnnotation, PythonVisitorContext visitorContext) {
        if (typeAnnotation == null || typeAnnotation.isBlank()) {
            return visitorContext.getClassElement(Object.class).orElse(ClassElement.of(Object.class));
        }

        // Handle Annotated types by extracting the base type
        if (typeAnnotation.startsWith("Annotated[")) {
            int bracketStart = typeAnnotation.indexOf('[');
            int firstComma = typeAnnotation.indexOf(',', bracketStart);
            if (firstComma != -1) {
                String baseType = typeAnnotation.substring(bracketStart + 1, firstComma).trim();
                return resolvePythonTypeToJava(baseType, visitorContext);
            }
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
