/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.convert;

import io.micronaut.core.annotation.Indexed;

import java.util.Optional;
import java.util.function.Function;

/**
 * <p>A type converter for converting from one type to another.</p>
 * <p>
 * <p>Implementations should be stateless, simple and thread safe. Type converters are often best defined as Java lambdas.
 * You should NOT perform any overly complex, blocking or slow conversions in implementations of this interface.
 * </p>
 * <p>
 * <p>If dependency injection is required, carefully consider what you inject. Databases and I/O bound interfaces are not good candidates.
 * In addition, injecting dependencies that may trigger the evaluation of beans that depend on configuration will cause problems because
 * all type converters have not been registered yet.</p>
 *
 * @param <S> The source type
 * @param <T> The target type
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(TypeConverter.class)
public interface TypeConverter<S, T> {

    /**
     * Converts from the given source object type to the target type.
     *
     * @param object     The object type
     * @param targetType The target type being converted to
     * @return The converted type or empty if the conversion is not possible
     */
    default Optional<T> convert(S object, Class<T> targetType) {
        return convert(object, targetType, ConversionContext.DEFAULT);
    }

    /**
     * Converts from the given source object type to the target type. Implementers should take care to return {@link Optional#empty()}
     * in case the object is not convertible by catching any necessary exceptions and failing gracefully.
     *
     * @param object     The object type
     * @param targetType The target type being converted to
     * @param context    The {@link ConversionContext}
     * @return The converted type or empty if the conversion is not possible
     */
    Optional<T> convert(S object, Class<T> targetType, ConversionContext context);

    /**
     * Creates a new {@link TypeConverter} for the give source type, target type and conversion function.
     *
     * @param sourceType The source type
     * @param targetType The target type
     * @param converter  The converter function
     * @param <ST>       The source generic type
     * @param <TT>       The target generic type
     * @return The converter instance
     */
    static <ST, TT> TypeConverter<ST, TT> of(Class<ST> sourceType, Class<TT> targetType, Function<ST, TT> converter) {
        return (object, targetType1, context) -> Optional.ofNullable(converter.apply(object));
    }
}
