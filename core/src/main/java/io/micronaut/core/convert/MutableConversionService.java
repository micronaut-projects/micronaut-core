/*
 * Copyright 2017-2020 original authors
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

import java.util.function.Function;

/**
 * A version of {@link ConversionService} that supports adding new converters.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface MutableConversionService extends ConversionService {

    /**
     * Creates a new mutable conversion service that extends the shared conversion service.
     * In most cases the mutable service from the bean context should be used.
     *
     * @return A new mutable conversion service.
     */
    static MutableConversionService create() {
        return new DefaultMutableConversionService();
    }

    /**
     * Adds a type converter.
     *
     * @param sourceType    The source type
     * @param targetType    The target type
     * @param typeConverter The type converter
     * @param <S>           The source generic type
     * @param <T>           The target generic type
     */
    <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> typeConverter);

    /**
     * Adds a type converter.
     *
     * @param sourceType    The source type
     * @param targetType    The target type
     * @param typeConverter The type converter
     * @param <S>           The source generic type
     * @param <T>           The target generic type
     */
    <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter);

}
