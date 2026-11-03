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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A {@link TypeConverter} that converts to a primitive without boxing it. The {@code find} methods
 * return an empty value, after rejecting the value in the context, when it does not convert, like
 * {@link #convert(Object, Class, ConversionContext)}; the {@code get} methods throw a
 * {@link ConversionErrorException} instead.
 * <p>
 * The default implementations box through {@link #convert(Object, Class, ConversionContext)}, so
 * {@link #of(TypeConverter)} adapts any converter; a converter that parses the primitive itself
 * overrides them. A method is only meant for the converter of the matching wrapper type: the
 * {@code int} methods of a converter to {@link Integer}, and so on.
 *
 * @param <S> The source type
 * @param <T> The target type
 * @since 5.3.0
 */
@Experimental
public interface PrimitiveTargetTypeConverter<S, T> extends TypeConverter<S, T> {

    /**
     * Converts the value to an {@code int}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value, or empty if it does not convert
     */
    default OptionalInt findInt(S value, ConversionContext context) {
        Optional<T> converted = convert(value, target(Integer.class), context);
        return converted.isPresent() ? OptionalInt.of((Integer) converted.get()) : OptionalInt.empty();
    }

    /**
     * Converts the value to an {@code int}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value
     * @throws ConversionErrorException if it does not convert
     */
    default int getInt(S value, ConversionContext context) {
        return (Integer) required(value, Argument.INT, context);
    }

    /**
     * Converts the value to a {@code long}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value, or empty if it does not convert
     */
    default OptionalLong findLong(S value, ConversionContext context) {
        Optional<T> converted = convert(value, target(Long.class), context);
        return converted.isPresent() ? OptionalLong.of((Long) converted.get()) : OptionalLong.empty();
    }

    /**
     * Converts the value to a {@code long}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value
     * @throws ConversionErrorException if it does not convert
     */
    default long getLong(S value, ConversionContext context) {
        return (Long) required(value, Argument.LONG, context);
    }

    /**
     * Converts the value to a {@code double}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value, or empty if it does not convert
     */
    default OptionalDouble findDouble(S value, ConversionContext context) {
        Optional<T> converted = convert(value, target(Double.class), context);
        return converted.isPresent() ? OptionalDouble.of((Double) converted.get()) : OptionalDouble.empty();
    }

    /**
     * Converts the value to a {@code double}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value
     * @throws ConversionErrorException if it does not convert
     */
    default double getDouble(S value, ConversionContext context) {
        return (Double) required(value, Argument.DOUBLE, context);
    }

    /**
     * Converts the value to a {@code boolean}.
     *
     * @param value   The value
     * @param context The conversion context
     * @return The converted value
     * @throws ConversionErrorException if it does not convert
     */
    default boolean getBoolean(S value, ConversionContext context) {
        return (Boolean) required(value, Argument.BOOLEAN, context);
    }

    /**
     * The converter that converts to primitives itself, without calling
     * {@link #convert(Object, Class, ConversionContext)}, which a caller may call with a context that does
     * not record errors, e.g. {@link ConversionContext#DEFAULT}; or {@code null} if the primitive methods
     * box through {@link #convert(Object, Class, ConversionContext)}, which needs the context of the
     * argument. A converter that parses the primitive returns itself; a resolved converter returns the
     * one it holds when that one does.
     *
     * @return The converter, or {@code null}
     */
    default @Nullable PrimitiveTargetTypeConverter<S, T> primitiveConverter() {
        return null;
    }

    /**
     * The given converter if it converts to primitives itself, or one that converts to them by
     * boxing through it.
     *
     * @param converter The converter
     * @param <S>       The source type
     * @param <T>       The target type
     * @return The converter
     */
    static <S, T> PrimitiveTargetTypeConverter<S, T> of(TypeConverter<S, T> converter) {
        if (converter instanceof PrimitiveTargetTypeConverter<S, T> primitive) {
            return primitive;
        }
        return converter::convert;
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> target(Class<?> wrapperType) {
        return (Class<T>) wrapperType;
    }

    private Object required(S value, Argument<?> argument, ConversionContext context) {
        Optional<T> converted = convert(value, target(argument.getWrapperType()), context);
        if (converted.isPresent()) {
            return converted.get();
        }
        Optional<ConversionError> error = context.getLastError();
        if (error.isPresent()) {
            throw new ConversionErrorException(argument, error.get());
        }
        throw new ConversionErrorException(argument, new IllegalArgumentException("Cannot convert [" + value + "] to " + argument.getTypeName()));
    }
}
