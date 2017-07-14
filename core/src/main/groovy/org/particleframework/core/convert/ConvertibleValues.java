/*
 * Copyright 2017 original authors
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
package org.particleframework.core.convert;

import org.particleframework.core.reflect.GenericTypeUtils;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * An interface for classes that represent a map-like structure of values that can be converted
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConvertibleValues<V> extends ValueResolver, Iterable<Map.Entry<String, V>> {
    /**
     * @return The names of the values
     */
    Set<String> getNames();

    /**
     * @return The concrete type of the value
     */
    default Class<V> getValueType() {
        Class type = GenericTypeUtils.resolveInterfaceTypeArgument(getClass(), ConvertibleValues.class);
        return type != null ? type : (Class<V>) Object.class;
    }

    /**
     * Finds a header
     *
     * @param name The header name
     * @return True if it does
     */
    default boolean contains(CharSequence name) {
        return get(name, Object.class).isPresent();
    }

    /**
     * Performs the given action for each value. Note that in the case
     * where multiple values exist for the same header then the consumer will be invoked
     * multiple times for the same key
     *
     * @param action The action to be performed for each entry
     * @throws NullPointerException if the specified action is null
     * @since 1.0
     */
    default void forEach(BiConsumer<String, V> action) {
        Objects.requireNonNull(action, "Consumer cannot be null");

        Collection<String> headerNames = getNames();
        for (String headerName : headerNames) {
            Optional<V> vOptional = this.get(headerName, getValueType());
            vOptional.ifPresent(v -> action.accept(headerName, v));
        }
    }

    @Override
    default Iterator<Map.Entry<String, V>> iterator() {
        Iterator<String> headerNames = getNames().iterator();
        return new Iterator<Map.Entry<String, V>>() {
            @Override
            public boolean hasNext() {
                return headerNames.hasNext();
            }

            @Override
            public Map.Entry<String, V> next() {
                if(!hasNext()) throw new NoSuchElementException();

                String name = headerNames.next();
                return new Map.Entry<String, V>() {
                    @Override
                    public String getKey() {
                        return name;
                    }

                    @Override
                    public V getValue() {
                        return (V) get(name, getValueType()).orElse(null);
                    }

                    @Override
                    public V setValue(V value) {
                        throw new UnsupportedOperationException("Not mutable");
                    }
                };
            }
        };
    }
}
