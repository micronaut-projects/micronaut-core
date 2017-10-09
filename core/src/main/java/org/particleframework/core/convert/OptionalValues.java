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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * A simple type safe abstraction over a
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface OptionalValues<V> extends Iterable<CharSequence> {

    /**
     * Constant for empty values
     */
    OptionalValues EMPTY_VALUES = of(Object.class, Collections.EMPTY_MAP);

    /**
     * Retrieve a value if it is present
     *
     * @param name The name of the value
     * @return An {@link Optional} of the value
     */
    Optional<V> get(CharSequence name);

    /**
     * An empty {@link OptionalValues}
     *
     * @param <T> The generic type
     * @return The empty values
     */
    static <T> OptionalValues<T> empty() {
        return EMPTY_VALUES;
    }
    /**
     * Creates a new {@link OptionalValues} for the given type and values
     *
     * @param type The target type
     * @param values A map of values
     * @param <T> The target generic type
     * @return The values
     */
    static <T> OptionalValues<T> of(Class<T> type, Map<CharSequence, ?> values ) {
        ValueResolver resolver = ValueResolver.of(values);
        return new OptionalValues<T>() {
            @Override
            public Optional<T> get(CharSequence name) {
                return resolver.get(name, type);
            }

            @Override
            public Iterator<CharSequence> iterator() {
                return values.keySet().iterator();
            }
        };
    }
}
