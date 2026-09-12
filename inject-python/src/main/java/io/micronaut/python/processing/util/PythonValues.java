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
import io.micronaut.core.annotation.Nullable;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the literal values the Python processor produces (decorator arguments, attribute
 * initialisers, parameter defaults) into plain Java values at the language boundary, so the rest of
 * the compiler never handles a polyglot {@link Value}.
 *
 * <p>Literals map to their natural Java type: {@code str} to {@link String}, {@code bool} to
 * {@link Boolean}, {@code int} to {@link Integer} or {@link Long}, {@code float} to {@link Double},
 * lists and tuples to {@link List}, dicts to {@link Map}. Host objects (a {@code DecoratorDef}, a
 * {@code TypeRef}, a Java class) pass through. A Python object that is none of these, for example a
 * {@code dataclasses.Field}, is kept as the {@link Value} it is.</p>
 *
 * @since 5.2.0
 */
@Internal
public final class PythonValues {

    private PythonValues() {
    }

    /**
     * Converts a value received from the processor.
     *
     * @param value The value, which may already be a Java object
     * @return The Java value
     */
    public static @Nullable Object toJava(@Nullable Object value) {
        if (value instanceof Value polyglotValue) {
            return toJava(polyglotValue);
        }
        if (value instanceof List<?> list) {
            // A guest list handed to a Java parameter arrives already coerced to a polyglot list rather than as a
            // Value, and reading it is only valid while the guest context is open. Copy it, so that a value which
            // outlives the compilation, such as an annotation member default, holds no guest handle.
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(toJava(element));
            }
            return copy;
        }
        return value;
    }

    /**
     * Converts a polyglot value received from the processor.
     *
     * @param value The polyglot value
     * @return The Java value
     */
    public static @Nullable Object toJava(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            if (value.fitsInDouble()) {
                return value.asDouble();
            }
            return value.toString();
        }
        if (value.hasHashEntries()) {
            Map<Object, Object> map = new LinkedHashMap<>();
            Value iterator = value.getHashEntriesIterator();
            while (iterator.hasIteratorNextElement()) {
                Value entry = iterator.getIteratorNextElement();
                map.put(toJava(entry.getArrayElement(0)), toJava(entry.getArrayElement(1)));
            }
            return map;
        }
        if (value.hasArrayElements() && !value.isMetaObject()) {
            int size = Math.toIntExact(value.getArraySize());
            List<Object> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(toJava(value.getArrayElement(i)));
            }
            return list;
        }
        return value;
    }

    /**
     * Converts every value of a map received from the processor.
     *
     * @param values The values keyed by member name
     * @return A new map with Java values, or the same map when it is null
     */
    public static @Nullable Map<String, Object> toJava(@Nullable Map<String, ?> values) {
        if (values == null) {
            return null;
        }
        Map<String, Object> converted = new LinkedHashMap<>(values.size());
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            converted.put(entry.getKey(), toJava(entry.getValue()));
        }
        return converted;
    }
}
