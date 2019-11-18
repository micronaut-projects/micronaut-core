/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.core.convert.value;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Specialization of {@link ConvertibleValues} where each name has multiple possible values.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConvertibleMultiValues<V> extends ConvertibleValues<List<V>> {
    /**
     * Get all the values for the given name without applying conversion.
     *
     * @param name The header name
     * @return All the values
     */
    List<V> getAll(CharSequence name);

    /**
     * Get a value without applying any conversion.
     *
     * @param name The name of the value
     * @return The raw value or null
     * @see #getFirst(CharSequence)
     */
    @Nullable V get(CharSequence name);

    /**
     * @return Whether this values is empty
     */
    @Override
    default boolean isEmpty() {
        return this == ConvertibleMultiValuesMap.EMPTY || names().isEmpty();
    }

    /**
     * Performs the given action for each header. Note that in the case
     * where multiple values exist for the same header then the consumer will be invoked
     * multiple times for the same key.
     *
     * @param action The action to be performed for each entry
     * @throws NullPointerException if the specified action is null
     * @since 1.0
     */
    default void forEachValue(BiConsumer<String, V> action) {
        Objects.requireNonNull(action, "Consumer cannot be null");

        Collection<String> names = names();
        for (String headerName : names) {
            Collection<V> values = getAll(headerName);
            for (V value : values) {
                action.accept(headerName, value);
            }
        }
    }

    @Override
    default void forEach(BiConsumer<String, List<V>> action) {
        Objects.requireNonNull(action, "Consumer cannot be null");

        Collection<String> names = names();
        for (String headerName : names) {
            List<V> values = getAll(headerName);
            action.accept(headerName, values);
        }
    }

    @Override
    default Iterator<Map.Entry<String, List<V>>> iterator() {
        Iterator<String> headerNames = names().iterator();
        return new Iterator<Map.Entry<String, List<V>>>() {
            @Override
            public boolean hasNext() {
                return headerNames.hasNext();
            }

            @Override
            public Map.Entry<String, List<V>> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                String name = headerNames.next();
                return new Map.Entry<String, List<V>>() {
                    @Override
                    public String getKey() {
                        return name;
                    }

                    @Override
                    public List<V> getValue() {
                        return getAll(name);
                    }

                    @Override
                    public List<V> setValue(List<V> value) {
                        throw new UnsupportedOperationException("Not mutable");
                    }
                };
            }
        };
    }

    /**
     * Get the first value of the given header.
     *
     * @param name The header name
     * @return The first value or null if it is present
     */
    default Optional<V> getFirst(CharSequence name) {
        Optional<Class> type = GenericTypeUtils.resolveInterfaceTypeArgument(getClass(), ConvertibleMultiValues.class);
        return getFirst(name, type.orElse(Object.class));
    }

    /**
     * Find a header and convert it to the given type.
     *
     * @param name         The name of the header
     * @param requiredType The required type
     * @param <T>          The generic type
     * @return If the header is presented and can be converted an optional of the value otherwise {@link Optional#empty()}
     */
    default <T> Optional<T> getFirst(CharSequence name, Class<T> requiredType) {
        return getFirst(name, Argument.of(requiredType));
    }

    /**
     * Find a header and convert it to the given type.
     *
     * @param name         The name of the header
     * @param requiredType The required type
     * @param <T>          The generic type
     * @return If the header is presented and can be converted an optional of the value otherwise {@link Optional#empty()}
     */
    default <T> Optional<T> getFirst(CharSequence name, Argument<T> requiredType) {
        V v = get(name);
        if (v != null) {
            return ConversionService.SHARED.convert(v, ConversionContext.of(requiredType));
        }
        return Optional.empty();
    }

    /**
     * Find a header and convert it to the given type.
     *
     * @param name              The name of the header
     * @param conversionContext The conversion context
     * @param <T>               The generic type
     * @return If the header is presented and can be converted an optional of the value otherwise {@link Optional#empty()}
     */
    default <T> Optional<T> getFirst(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        V v = get(name);
        if (v != null) {
            return ConversionService.SHARED.convert(v, conversionContext);
        }
        return Optional.empty();
    }

    /**
     * Find a header and convert it to the given type.
     *
     * @param name         The name of the header
     * @param requiredType The required type
     * @param defaultValue The default value
     * @param <T>          The generic type
     * @return The first value of the default supplied value if it is isn't present
     */
    default <T> T getFirst(CharSequence name, Class<T> requiredType, T defaultValue) {
        return getFirst(name, requiredType).orElse(defaultValue);
    }

    /**
     * Creates a new {@link io.micronaut.core.value.OptionalValues} for the given type and values.
     *
     * @param values A map of values
     * @param <T>    The target generic type
     * @return The values
     */
    static <T> ConvertibleMultiValues<T> of(Map<CharSequence, List<T>> values) {
        return new ConvertibleMultiValuesMap<>(values);
    }

    /**
     * An empty {@link ConvertibleValues}.
     *
     * @param <V> The generic type
     * @return The empty {@link ConvertibleValues}
     */
    @SuppressWarnings("unchecked")
    static <V> ConvertibleMultiValues<V> empty() {
        return ConvertibleMultiValuesMap.EMPTY;
    }
}
