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
package io.micronaut.core.value;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;

import java.util.Optional;

/**
 * An interface for any type that is able to resolve and convert values.
 *
 * @param <K> parent type
 * @author Graeme Rocher
 * @see io.micronaut.core.convert.TypeConverter
 * @since 1.0
 */
public interface ValueResolver<K extends CharSequence> {

    /**
     * Resolve the given property for the given name.
     *
     * @param name              The name
     * @param conversionContext The conversion context
     * @param <T>               The concrete type
     * @return An optional containing the property value if it exists and is able to be converted
     */
    <T> Optional<T> get(K name, ArgumentConversionContext<T> conversionContext);

    /**
     * Resolve the given property for the given name.
     *
     * @param name         The name
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the property value if it exists and is able to be converted
     */
    default <T> Optional<T> get(K name, Class<T> requiredType) {
        return get(name, ConversionContext.of(Argument.of(requiredType)));
    }

    /**
     * Resolve the given property for the given name.
     *
     * @param name         The name
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the property value if it exists and is able to be converted
     */
    default <T> Optional<T> get(K name, Argument<T> requiredType) {
        return get(name, ConversionContext.of(requiredType));
    }

    /**
     * Resolve the given property for the given name.
     *
     * @param name         The name
     * @param requiredType The required type
     * @param defaultValue The default value
     * @param <T>          The concrete type
     * @return Property value if it exists or default value
     */
    default <T> T get(K name, Class<T> requiredType, T defaultValue) {
        return get(name, requiredType).orElse(defaultValue);
    }
}
