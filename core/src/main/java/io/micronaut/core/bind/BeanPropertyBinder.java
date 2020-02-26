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
package io.micronaut.core.bind;

import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.exceptions.ConversionErrorException;

import java.util.Map;
import java.util.Set;

/**
 * <p>An interface that provides the ability to bind Maps and Java bean properties</p>.
 * <p>
 * <p>This class is designed specifically for binding of String based property data such as Form submissions and
 * dynamic binding of Java Properties files and should not be used beyond these two use cases.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanPropertyBinder extends ArgumentBinder<Object, Map<CharSequence, ? super Object>> {

    /**
     * Bind a new instance of the given type from the given source.
     *
     * @param type   The type
     * @param source The source
     * @param <T2>   The generic type
     * @return The bound instance
     * @throws ConversionErrorException if the object cannot be bound
     */
    @SuppressWarnings("unchecked")
    <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException;

    /**
     * Bind an existing instance of the given type from the given source.
     *
     * @param object  The bean
     * @param context The conversion context
     * @param source  The source
     * @param <T2>    The generic type
     * @return The bound instance
     */
    <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source);

    /**
     * Bind an existing instance of the given type from the given source.
     *
     * @param object The bean
     * @param source The source
     * @param <T2>   The generic type
     * @return The bound instance
     * @throws ConversionErrorException if the object cannot be bound
     */
    <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException;

    /**
     * Bind a new instance of the given type from the given source.
     *
     * @param type   The type
     * @param source The source
     * @param <T2>   The generic type
     * @return The bound instance
     * @throws ConversionErrorException if the object cannot be bound
     */
    @SuppressWarnings("unchecked")
    default <T2> T2 bind(Class<T2> type, Map<? extends CharSequence, Object> source) throws ConversionErrorException {
        return bind(type, source.entrySet());
    }

    /**
     * Bind an existing instance of the given type from the given source.
     *
     * @param object  The bean
     * @param context The conversion context
     * @param source  The source
     * @param <T2>    The generic type
     * @return The bound instance
     */
    default <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Map<? extends CharSequence, Object> source) {
        return bind(object, context, source.entrySet());
    }

    /**
     * Bind an existing instance of the given type from the given source.
     *
     * @param object The bean
     * @param source The source
     * @param <T2>   The generic type
     * @return The bound instance
     * @throws ConversionErrorException if the object cannot be bound
     */
    default <T2> T2 bind(T2 object, Map<? extends CharSequence, Object> source) throws ConversionErrorException {
        return bind(object, source.entrySet());
    }

    /**
     * Bind an existing instance of the given type from the given source.
     *
     * @param object The bean
     * @param source The source
     * @param <T2>   The generic type
     * @return The bound instance
     * @throws ConversionErrorException if the object cannot be bound
     */
    default <T2> T2 bind(T2 object, Object source) throws ConversionErrorException {
        return bind(object, BeanMap.of(source).entrySet());
    }
}
