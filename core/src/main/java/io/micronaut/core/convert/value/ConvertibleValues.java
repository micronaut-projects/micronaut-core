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
import io.micronaut.core.value.ValueResolver;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * An interface for classes that represent a map-like structure of values that can be converted.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConvertibleValues<V> extends ValueResolver<CharSequence>, Iterable<Map.Entry<String, V>> {

    ConvertibleValues EMPTY = new ConvertibleValuesMap<>(Collections.emptyMap());

    /**
     * @return The names of the values
     */
    Set<String> names();

    /**
     * @return The values
     */
    Collection<V> values();

    /**
     * @return Whether this values is empty
     */
    default boolean isEmpty() {
        return this == ConvertibleValues.EMPTY || names().isEmpty();
    }

    /**
     * @return The concrete type of the value
     */
    @SuppressWarnings("unchecked")
    default Class<V> getValueType() {
        Optional<Class> type = GenericTypeUtils.resolveInterfaceTypeArgument(getClass(), ConvertibleValues.class);
        return type.orElse(Object.class);
    }

    /**
     * Whether the given key is contained within these values.
     *
     * @param name The key name
     * @return True if it is
     */
    default boolean contains(String name) {
        return get(name, Argument.OBJECT_ARGUMENT).isPresent();
    }

    /**
     * Performs the given action for each value. Note that in the case
     * where multiple values exist for the same header then the consumer will be invoked
     * multiple times for the same key.
     *
     * @param action The action to be performed for each entry
     * @throws NullPointerException if the specified action is null
     * @since 1.0
     */
    default void forEach(BiConsumer<String, V> action) {
        Objects.requireNonNull(action, "Consumer cannot be null");

        Collection<String> headerNames = names();
        for (String headerName : headerNames) {
            Optional<V> vOptional = this.get(headerName, getValueType());
            vOptional.ifPresent(v -> action.accept(headerName, v));
        }
    }

    /**
     * Return this {@link ConvertibleValues} as a map for the given key type and value type. The map represents a copy of the data held by this instance.
     *
     * @return The values
     */
    default Map<String, V> asMap() {
        Map<String, V> newMap = new LinkedHashMap<>();
        for (Map.Entry<String, V> entry : this) {
            String key = entry.getKey();
            newMap.put(key, entry.getValue());
        }
        return newMap;
    }

    /**
     * Return this {@link ConvertibleValues} as a map for the given key type and value type. If any entry cannot be
     * converted to the target key/value type then the entry is simply excluded, hence the size of the map returned
     * may not match the size of this {@link ConvertibleValues}.
     *
     * @param keyType   The key type
     * @param valueType The value type
     * @param <KT>      The key type
     * @param <VT>      The value type
     * @return The values with the key converted to the given key type and the value to the given value type.
     */
    default <KT, VT> Map<KT, VT> asMap(Class<KT> keyType, Class<VT> valueType) {
        Map<KT, VT> newMap = new LinkedHashMap<>();
        for (Map.Entry<String, V> entry : this) {
            String key = entry.getKey();
            Optional<KT> convertedKey = ConversionService.SHARED.convert(key, keyType);
            if (convertedKey.isPresent()) {
                Optional<VT> convertedValue = ConversionService.SHARED.convert(entry.getValue(), valueType);
                convertedValue.ifPresent(vt -> newMap.put(convertedKey.get(), vt));
            }
        }
        return newMap;
    }

    /**
     * Return this {@link ConvertibleValues} as a {@link Properties} object returning only keys and values that
     * can be represented as a string.
     *
     * @return The values with the key converted to the given key type and the value to the given value type.
     * @since 1.0.3
     */
    default Properties asProperties() {
        Properties props = new Properties();

        for (Map.Entry<String, V> entry : this) {
            String key = entry.getKey();
            V value = entry.getValue();
            if (value instanceof CharSequence || value instanceof Number) {
                props.setProperty(key, value.toString());
            }
        }
        return props;
    }

    /**
     * Returns a submap for all the keys with the given prefix.
     *
     * @param prefix    The prefix
     * @param valueType The value type
     * @return The submap
     */
    @SuppressWarnings("unchecked")
    default Map<String, V> subMap(String prefix, Class<V> valueType) {
        return subMap(prefix, Argument.of(valueType));
    }

    /**
     * Returns a submap for all the keys with the given prefix.
     *
     * @param prefix    The prefix
     * @param valueType The value type
     * @return The submap
     */
    @SuppressWarnings("unchecked")
    default Map<String, V> subMap(String prefix, Argument<V> valueType) {
        return subMap(prefix, ConversionContext.of(valueType));
    }

    /**
     * Returns a submap for all the keys with the given prefix.
     *
     * @param prefix    The prefix
     * @param valueType The value type
     * @return The submap
     */
    @SuppressWarnings("unchecked")
    default Map<String, V> subMap(String prefix, ArgumentConversionContext<V> valueType) {
        // special handling for maps for resolving sub keys
        String finalPrefix = prefix + '.';
        return names().stream()
            .filter(name -> name.startsWith(finalPrefix))
            .collect(Collectors.toMap((name) -> name.substring(finalPrefix.length()), (name) -> get(name, valueType).orElse(null)));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    default Iterator<Map.Entry<String, V>> iterator() {
        Iterator<String> names = names().iterator();
        return new Iterator<Map.Entry<String, V>>() {
            @Override
            public boolean hasNext() {
                return names.hasNext();
            }

            @Override
            public Map.Entry<String, V> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                String name = names.next();
                return new Map.Entry<String, V>() {
                    @Override
                    public String getKey() {
                        return name;
                    }

                    @Override
                    public V getValue() {
                        return get(name, getValueType()).orElse(null);
                    }

                    @Override
                    public V setValue(V value) {
                        throw new UnsupportedOperationException("Not mutable");
                    }
                };
            }
        };
    }

    /**
     * Creates a new {@link ConvertibleValues} for the values.
     *
     * @param values A map of values
     * @param <T>    The target generic type
     * @return The values
     */
    static <T> ConvertibleValues<T> of(Map<? extends CharSequence, T> values) {
        if (values == null) {
            return ConvertibleValuesMap.empty();
        } else {
            return new ConvertibleValuesMap<>(values);
        }
    }

    /**
     * An empty {@link ConvertibleValues}.
     *
     * @param <V> The generic type
     * @return The empty {@link ConvertibleValues}
     */
    @SuppressWarnings("unchecked")
    static <V> ConvertibleValues<V> empty() {
        return ConvertibleValues.EMPTY;
    }
}
