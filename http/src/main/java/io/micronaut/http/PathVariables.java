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
import io.micronaut.core.type.Argument;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The path variables of the route a request matched: every variable of the URI template of the
 * route, e.g. {@code id} of {@code /items/{id}}.
 *
 * <p>A controller method, or a request filter method of a server filter that runs after the
 * request is routed, declares a parameter of this type to read them:</p>
 *
 * <pre>{@code
 * @Get("/items/{id}")
 * Item item(PathVariables pathVariables) {
 *     return items.find(pathVariables.getLong("id"));
 * }
 * }</pre>
 *
 * <p>There are accessors for strings and the primitive types, e.g. {@code getLong("id")} or
 * {@code findInt("page")}, with a default value for a missing variable, e.g.
 * {@code getInt("page", 0)}, and {@link #get(String, Class)} and {@link #find(String, Class)} for
 * any other type, e.g. {@code UUID}, or {@link #get(String, Argument)} and
 * {@link #find(String, Argument)} for a generic type, e.g. {@code List<Integer>} of an exploded
 * variable, or the list accessors {@link #getStrings(String)}, {@link #getList(String, Class)}
 * and {@link #findList(String, Class)}. Values convert with the conversion service of the route,
 * like the {@link io.micronaut.http.annotation.PathVariable} arguments of a controller method,
 * and fail the same way: a missing variable or a value that does not convert is answered with
 * 400.</p>
 *
 * <p>A request is routed after the pre-matching filters ran, so a
 * {@code @PreMatching} filter method cannot declare it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface PathVariables {

    /**
     * @return The names of the variables that have a value
     */
    Set<String> names();

    /**
     * @param name The name of the variable
     * @return Whether the variable has a value
     */
    boolean contains(String name);

    /**
     * A required variable converted to a type.
     *
     * @param name The name of the variable
     * @param type The type
     * @param <T>  The type
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default <T> T get(String name, Class<T> type) {
        return get(name, Argument.of(type, name));
    }

    /**
     * An optional variable converted to a type.
     *
     * @param name The name of the variable
     * @param type The type
     * @param <T>  The type
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default <T> Optional<T> find(String name, Class<T> type) {
        return find(name, Argument.of(type, name));
    }

    /**
     * A required variable converted to a type, with its type arguments, like a path variable
     * argument of a controller method of that type, e.g. {@code Argument.listOf(Integer.class)}.
     *
     * @param name The name of the variable
     * @param type The type
     * @param <T>  The type
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     * @since 5.3.0
     */
    <T> T get(String name, Argument<T> type);

    /**
     * An optional variable converted to a type, with its type arguments.
     *
     * @param name The name of the variable
     * @param type The type
     * @param <T>  The type
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     * @since 5.3.0
     */
    <T> Optional<T> find(String name, Argument<T> type);

    /**
     * The values of a required variable as strings, like a {@code List<String>} path variable
     * argument of a controller method: the values of an exploded variable, e.g. of
     * {@code /files{/path*}}, or of a comma separated value, split the same way, and a single
     * value is a list of one element.
     *
     * <pre>{@code
     * @Get("/tags/{tags}")
     * List<Post> tagged(PathVariables pathVariables) {
     *     return posts.tagged(pathVariables.getStrings("tags"));
     * }
     * }</pre>
     *
     * @param name The name of the variable
     * @return The values
     * @throws RuntimeException if the variable has no value, answered with 400
     * @since 5.3.0
     */
    @Experimental
    default List<String> getStrings(String name) {
        return getList(name, String.class);
    }

    /**
     * The values of a required variable, each converted to a type, like a {@code List} path
     * variable argument of a controller method, see {@link #getStrings(String)}: with the
     * conversion service of the route, which leaves out a value that does not convert to the
     * type, as it does for a controller argument.
     *
     * @param name The name of the variable
     * @param type The type of the elements
     * @param <T>  The type of the elements
     * @return The values
     * @throws RuntimeException if the variable has no value, answered with 400
     * @since 5.3.0
     */
    @Experimental
    default <T> List<T> getList(String name, Class<T> type) {
        return getList(name, Argument.of(type));
    }

    /**
     * The values of a required variable, each converted to a type with its type arguments, see
     * {@link #getList(String, Class)}.
     *
     * @param name The name of the variable
     * @param type The type of the elements
     * @param <T>  The type of the elements
     * @return The values
     * @throws RuntimeException if the variable has no value, answered with 400
     * @since 5.3.0
     */
    @Experimental
    default <T> List<T> getList(String name, Argument<T> type) {
        return get(name, Argument.listOf(type));
    }

    /**
     * The values of an optional variable, each converted to a type, see
     * {@link #getList(String, Class)}.
     *
     * @param name The name of the variable
     * @param type The type of the elements
     * @param <T>  The type of the elements
     * @return The values, if the variable has a value
     * @since 5.3.0
     */
    @Experimental
    default <T> Optional<List<T>> findList(String name, Class<T> type) {
        return find(name, Argument.listOf(type));
    }

    /**
     * A required variable as a string.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default String getString(String name) {
        return get(name, String.class);
    }

    /**
     * A required variable as an {@code int}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default int getInt(String name) {
        return get(name, Integer.class);
    }

    /**
     * A required variable as a {@code long}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default long getLong(String name) {
        return get(name, Long.class);
    }

    /**
     * A required variable as a {@code double}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default double getDouble(String name) {
        return get(name, Double.class);
    }

    /**
     * A required variable as a {@code float}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default float getFloat(String name) {
        return get(name, Float.class);
    }

    /**
     * A required variable as a {@code short}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default short getShort(String name) {
        return get(name, Short.class);
    }

    /**
     * A required variable as a {@code byte}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default byte getByte(String name) {
        return get(name, Byte.class);
    }

    /**
     * A required variable as a {@code char}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default char getChar(String name) {
        return get(name, Character.class);
    }

    /**
     * A required variable as a {@code boolean}.
     *
     * @param name The name of the variable
     * @return The value
     * @throws RuntimeException if the variable has no value, answered with 400
     * @throws ConversionErrorException if the value does not convert, answered with 400
     */
    default boolean getBoolean(String name) {
        return get(name, Boolean.class);
    }

    /**
     * A variable converted to a type, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param type         The type
     * @param defaultValue The value to return if the variable has no value
     * @param <T>          The type
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default <T> T get(String name, Class<T> type, T defaultValue) {
        return find(name, type).orElse(defaultValue);
    }

    /**
     * A variable as a string, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default String getString(String name, String defaultValue) {
        return find(name, String.class).orElse(defaultValue);
    }

    /**
     * A variable as an {@code int}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default int getInt(String name, int defaultValue) {
        return find(name, Integer.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code long}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default long getLong(String name, long defaultValue) {
        return find(name, Long.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code double}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default double getDouble(String name, double defaultValue) {
        return find(name, Double.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code float}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default float getFloat(String name, float defaultValue) {
        return find(name, Float.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code short}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default short getShort(String name, short defaultValue) {
        return find(name, Short.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code byte}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default byte getByte(String name, byte defaultValue) {
        return find(name, Byte.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code char}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default char getChar(String name, char defaultValue) {
        return find(name, Character.class).orElse(defaultValue);
    }

    /**
     * A variable as a {@code boolean}, or a default value if it has none.
     *
     * @param name         The name of the variable
     * @param defaultValue The value to return if the variable has no value
     * @return The value, or the default value
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default boolean getBoolean(String name, boolean defaultValue) {
        return find(name, Boolean.class).orElse(defaultValue);
    }

    /**
     * An optional variable as a string.
     *
     * @param name The name of the variable
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default Optional<String> findString(String name) {
        return find(name, String.class);
    }

    /**
     * An optional variable as an {@code int}.
     *
     * @param name The name of the variable
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalInt findInt(String name) {
        Optional<Integer> value = find(name, Integer.class);
        return value.isPresent() ? OptionalInt.of(value.get()) : OptionalInt.empty();
    }

    /**
     * An optional variable as a {@code long}.
     *
     * @param name The name of the variable
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalLong findLong(String name) {
        Optional<Long> value = find(name, Long.class);
        return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
    }

    /**
     * An optional variable as a {@code double}.
     *
     * @param name The name of the variable
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default OptionalDouble findDouble(String name) {
        Optional<Double> value = find(name, Double.class);
        return value.isPresent() ? OptionalDouble.of(value.get()) : OptionalDouble.empty();
    }

    /**
     * An optional variable as a {@code boolean}.
     *
     * @param name The name of the variable
     * @return The value, if present
     * @throws ConversionErrorException if the value is present but does not convert, answered with 400
     */
    default Optional<Boolean> findBoolean(String name) {
        return find(name, Boolean.class);
    }
}
