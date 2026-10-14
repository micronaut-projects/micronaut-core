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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.exceptions.ConversionErrorException;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Named values of a request that convert to types: the {@link PathVariables} of the route it
 * matched, or the text fields of its {@link io.micronaut.http.form.FormData form}. There are
 * accessors for strings and the primitive types, e.g. {@code getLong("id")} or
 * {@code findInt("page")}, with a default value for a missing value, e.g.
 * {@code getInt("page", 0)}. A missing required value or a value that does not convert is
 * answered with 400.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface NamedValues {

    /**
     * A required value converted to a type.
     *
     * @param name The name of the value
     * @param type The type
     * @param <T>  The type
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    <T> T get(String name, Class<T> type);

    /**
     * An optional value converted to a type.
     *
     * @param name The name of the value
     * @param type The type
     * @param <T>  The type
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    <T> Optional<T> find(String name, Class<T> type);

    /**
     * A required value as a string.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default String getString(String name) {
        return get(name, String.class);
    }

    /**
     * A required value as an {@code int}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default int getInt(String name) {
        return get(name, Integer.class);
    }

    /**
     * A required value as a {@code long}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default long getLong(String name) {
        return get(name, Long.class);
    }

    /**
     * A required value as a {@code double}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default double getDouble(String name) {
        return get(name, Double.class);
    }

    /**
     * A required value as a {@code float}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default float getFloat(String name) {
        return get(name, Float.class);
    }

    /**
     * A required value as a {@code short}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default short getShort(String name) {
        return get(name, Short.class);
    }

    /**
     * A required value as a {@code byte}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default byte getByte(String name) {
        return get(name, Byte.class);
    }

    /**
     * A required value as a {@code char}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default char getChar(String name) {
        return get(name, Character.class);
    }

    /**
     * A required value as a {@code boolean}.
     *
     * @param name The name of the value
     * @return The value
     * @throws RuntimeException if there is no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default boolean getBoolean(String name) {
        return get(name, Boolean.class);
    }

    /**
     * A value converted to a type, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param type         The type
     * @param defaultValue The value to return if there is none
     * @param <T>          The type
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default <T> T get(String name, Class<T> type, T defaultValue) {
        return find(name, type).orElse(defaultValue);
    }

    /**
     * A value as a string, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default String getString(String name, String defaultValue) {
        return find(name, String.class).orElse(defaultValue);
    }

    /**
     * A value as an {@code int}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default int getInt(String name, int defaultValue) {
        return find(name, Integer.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code long}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default long getLong(String name, long defaultValue) {
        return find(name, Long.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code double}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default double getDouble(String name, double defaultValue) {
        return find(name, Double.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code float}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default float getFloat(String name, float defaultValue) {
        return find(name, Float.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code short}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default short getShort(String name, short defaultValue) {
        return find(name, Short.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code byte}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default byte getByte(String name, byte defaultValue) {
        return find(name, Byte.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code char}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default char getChar(String name, char defaultValue) {
        return find(name, Character.class).orElse(defaultValue);
    }

    /**
     * A value as a {@code boolean}, or a default value if there is none.
     *
     * @param name         The name of the value
     * @param defaultValue The value to return if there is none
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default boolean getBoolean(String name, boolean defaultValue) {
        return find(name, Boolean.class).orElse(defaultValue);
    }

    /**
     * An optional value as a string.
     *
     * @param name The name of the value
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default Optional<String> findString(String name) {
        return find(name, String.class);
    }

    /**
     * An optional value as an {@code int}.
     *
     * @param name The name of the value
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalInt findInt(String name) {
        Optional<Integer> value = find(name, Integer.class);
        return value.isPresent() ? OptionalInt.of(value.get()) : OptionalInt.empty();
    }

    /**
     * An optional value as a {@code long}.
     *
     * @param name The name of the value
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalLong findLong(String name) {
        Optional<Long> value = find(name, Long.class);
        return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
    }

    /**
     * An optional value as a {@code double}.
     *
     * @param name The name of the value
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalDouble findDouble(String name) {
        Optional<Double> value = find(name, Double.class);
        return value.isPresent() ? OptionalDouble.of(value.get()) : OptionalDouble.empty();
    }

    /**
     * An optional value as a {@code boolean}.
     *
     * @param name The name of the value
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default Optional<Boolean> findBoolean(String name) {
        return find(name, Boolean.class);
    }
}
