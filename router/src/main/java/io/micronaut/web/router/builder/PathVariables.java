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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The path variables of the route a request matched, given to handler functions:
 *
 * <pre>{@code
 * routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id"))));
 * }</pre>
 *
 * <p>There are accessors for strings and the primitive types, e.g. {@code getLong("id")} or
 * {@code findInt("page")}, with a default value for a missing variable, e.g.
 * {@code getInt("page", 0)}, and {@link #get(String, Class)} and {@link #find(String, Class)} for
 * any other type, e.g. {@code UUID}, or {@link #get(String, Argument)} and
 * {@link #find(String, Argument)} for a generic type, e.g. {@code List<Integer>} of an exploded
 * variable. Values convert with the conversion service of the route,
 * like the path variable arguments of a controller method, and fail the same way: a missing
 * variable or a value that does not convert is answered with 400.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface PathVariables permits DefaultPathVariables {

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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * A required variable as a string.
     *
     * @param name The name of the variable
     * @return The value
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
     * @throws UnsatisfiedPathVariableRouteException if the variable has no value, answered with 400
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
        return value.isPresent() ? OptionalInt.of(value.get().intValue()) : OptionalInt.empty();
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
        return value.isPresent() ? OptionalLong.of(value.get().longValue()) : OptionalLong.empty();
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
        return value.isPresent() ? OptionalDouble.of(value.get().doubleValue()) : OptionalDouble.empty();
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

    /**
     * The target a locator returned for the route, see
     * {@link HttpRouteBuilder#locate(String, LocatorHandler, java.util.function.Function)}: the
     * innermost one when locators locate each other.
     *
     * @return The target, or {@code null} for a route that was not located
     * @since 5.3.0
     */
    default @Nullable Object locatedTarget() {
        return null;
    }

    /**
     * The target a locator returned for the route, of a type.
     *
     * @param type The type of the target
     * @param <T>  The type of the target
     * @return The target
     * @throws IllegalStateException if the route was not located, or the target is not of the type
     * @since 5.3.0
     */
    default <T> T locatedTarget(Class<T> type) {
        Object target = locatedTarget();
        if (!type.isInstance(target)) {
            throw new IllegalStateException("The located target is not a " + type.getName() + ": " + target);
        }
        return type.cast(target);
    }
}
