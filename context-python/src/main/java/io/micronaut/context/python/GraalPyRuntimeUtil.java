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
package io.micronaut.context.python;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Value;

/**
 * Runtime utility class for converting GraalPy Values to Java collections.
 * Provides type-safe conversion methods for List, Map, and other collection types.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Internal
public final class GraalPyRuntimeUtil {

    public static final String PYTHON = "python";

    /**
     * Coerce a map of types that may extend from {@link ValueCoercible} back to a native value map.
     * @param map The map
     * @param <V> The value type of the map
     * @return The resulting map
     */
    public static <V> Map<String, Object> coerceMap(Map<String, V> map) {
        return
            map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> {
                Object v = entry.getValue();
                if (v instanceof ValueCoercible valueCoercible) {
                    return valueCoercible.asPolyglotValue();
                }
                return v;
            }));
    }

    /**
     * Coerce a list of types that may extend from {@link ValueCoercible} back to a native value list.
     * @param list The list
     * @param <E> The element type of the list
     * @return The resulting list
     *
     */
    public static <E> List<Object> coerceList(List<E> list) {
        return
            list.stream().map(v -> {
                if (v instanceof ValueCoercible valueCoercible) {
                    return valueCoercible.asPolyglotValue();
                }
                return v;
            }).toList();
    }

    /**
     * Convert a GraalPy Value representing a list to a Java List.
     * Recursively converts nested collections.
     *
     * @param graalValue the GraalPy Value (should be a list-like object)
     * @param elementType the expected element type for conversion
     * @return a Java List with converted elements
     */
    public static <T> List<T> convertList(Value graalValue, Class<T> elementType) {
        // TODO: Ideally a custom List implementation that doesn't create a new list would be better here
        if (graalValue == null || graalValue.isNull()) {
            return new ArrayList<>();
        }

        try {
            // Try to get the size using different methods
            long size = getSize(graalValue);
            if (size == 0) {
                return List.of();
            }

            List<T> result = new ArrayList<>(Long.valueOf(size).intValue());

            // Convert each element
            for (long i = 0; i < size; i++) {
                Value elementValue = getElementAt(graalValue, i);
                if (elementValue != null) {
                    T convertedElement = convertValue(elementValue, elementType);
                    result.add(convertedElement);
                }
            }
            return result;
        } catch (Exception e) {
            // If conversion fails, return empty list
            return List.of();
        }
    }

    /**
     * Convert a GraalPy Value representing a dict to a Java Map.
     * Recursively converts nested collections.
     *
     * @param graalValue the GraalPy Value (should be a dict-like object)
     * @param keyType the expected key type for conversion
     * @param valueType the expected value type for conversion
     * @return a Java Map with converted keys and values
     */
    public static <K, V> Map<K, V> convertMap(Value graalValue, Class<K> keyType, Class<V> valueType) {
        // TODO: Ideally a custom Map implementation that doesn't create a new map would be better here
        if (graalValue == null || graalValue.isNull()) {
            return new HashMap<>();
        }

        Map<K, V> result = new HashMap<>();

        try {
            // Try to use Python dict iteration
            Value keysValue = graalValue.invokeMember("keys");
            if (keysValue != null && keysValue.hasIterator()) {
                Value iterator = keysValue.invokeMember("__iter__");
                while (true) {
                    try {
                        Value nextValue = iterator.invokeMember("__next__");
                        if (nextValue == null || nextValue.isNull()) {
                            break;
                        }

                        try {
                            K convertedKey = convertValue(nextValue, keyType);
                            Value mapValue = graalValue.invokeMember("__getitem__", nextValue);
                            V convertedValue = convertValue(mapValue, valueType);

                            result.put(convertedKey, convertedValue);
                        } catch (Exception e) {
                            // Skip problematic entries
                            continue;
                        }
                    } catch (Exception e) {
                        // Iterator exhausted (StopIteration in Python)
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // If conversion fails, return empty map
            return new HashMap<>();
        }

        return result;
    }

    /**
     * Convert a GraalPy Value representing an Optional to a Java Optional.
     * Handles None -> Optional.empty() and value -> Optional.of(value)
     *
     * @param graalValue the GraalPy Value to convert
     * @param elementType the expected element type for conversion
     * @return a Java Optional with the converted value or empty
     */
    @SuppressWarnings("unchecked")
    public static <T> java.util.Optional<T> convertOptional(Value graalValue, Class<T> elementType) {
        if (graalValue == null || graalValue.isNull()) {
            return java.util.Optional.empty();
        }

        // Check if the value is Python None
        if (graalValue.isNull()) {
            return java.util.Optional.empty();
        }

        // Convert the value and wrap in Optional
        T convertedValue = convertValue(graalValue, elementType);
        if (convertedValue == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(convertedValue);
    }

    /**
     * Convert a GraalPy Value representing a set to a Java Set.
     * Recursively converts nested collections.
     *
     * @param graalValue the GraalPy Value (should be a set-like object)
     * @param elementType the expected element type for conversion
     * @return a Java Set with converted elements
     */
    public static <T> Set<T> convertSet(Value graalValue, Class<T> elementType) {
        // TODO: Ideally a custom Set implementation that doesn't create a new map would be better here
        if (graalValue == null || graalValue.isNull()) {
            return new java.util.HashSet<>();
        }

        java.util.Set<T> result = new java.util.HashSet<>();

        try {
            // Try to iterate directly over the set
            if (graalValue.hasIterator()) {
                Value iterator = graalValue.invokeMember("__iter__");
                while (true) {
                    try {
                        Value nextValue = iterator.invokeMember("__next__");
                        if (nextValue == null || nextValue.isNull()) {
                            break;
                        }

                        try {
                            T convertedElement = convertValue(nextValue, elementType);
                            result.add(convertedElement);
                        } catch (Exception e) {
                            // Skip problematic elements
                        }
                    } catch (Exception e) {
                        // Iterator exhausted (StopIteration in Python)
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // If direct iteration fails, try converting to list first
            try {
                Value listValue = graalValue.invokeMember("list");
                if (listValue != null) {
                    List<T> list = convertList(listValue, elementType);
                    result.addAll(list);
                }
            } catch (Exception ex) {
                // If conversion fails, return empty set
                return new java.util.HashSet<>();
            }
        }

        return result;
    }

    /**
     * Generic value conversion method that handles primitives and recursively converts collections.
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertValue(Value value, Class<T> targetType) {
        if (value == null || value.isNull()) {
            return null;
        }

        return value.as(targetType);
    }

    /**
     * Get the size of a GraalPy collection using various methods.
     */
    private static long getSize(Value value) {
        try {
            // Try __len__ first (Python standard)
            if (value.canInvokeMember("__len__")) {
                Value length = value.invokeMember("__len__");
                return length.asLong();
            }

            // Try len() function
            if (value.canInvokeMember("__len__")) {
                Value length = value.invokeMember("__len__");
                return length.asLong();
            }

            // For arrays, try getArraySize
            try {
                return value.getArraySize();
            } catch (Exception e) {
                // Not an array
            }

            // If all else fails, try to iterate and count
            if (value.hasIterator()) {
                long count = 0;
                Value iterator = value.invokeMember("__iter__");
                while (true) {
                    try {
                        iterator.invokeMember("__next__");
                        count++;
                    } catch (Exception e) {
                        break;
                    }
                }
                return count;
            }
        } catch (Exception e) {
            // Size determination failed
        }

        return 0;
    }

    /**
     * Get element at index from a GraalPy collection.
     */
    private static Value getElementAt(Value collection, long index) {
        try {
            // Try array access first
            try {
                return collection.getArrayElement(index);
            } catch (Exception e) {
                // Not an array, try __getitem__
            }

            // Try __getitem__ method
            if (collection.canInvokeMember("__getitem__")) {
                return collection.invokeMember("__getitem__", index);
            }

            // Try iteration to specific index
            if (collection.hasIterator()) {
                Value iterator = collection.invokeMember("__iter__");
                for (long i = 0; i <= index; i++) {
                    Value item = iterator.invokeMember("__next__");
                    if (i == index) {
                        return item;
                    }
                }
            }
        } catch (Exception e) {
            // Element access failed
        }

        return null;
    }
}
