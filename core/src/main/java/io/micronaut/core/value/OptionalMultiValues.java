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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link OptionalValues} that for each key features an {@link java.util.Optional} {@link List} of values.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public interface OptionalMultiValues<V> extends OptionalValues<List<V>> {

    /**
     * Constant for empty values.
     */
    OptionalMultiValues EMPTY_VALUES = of(Collections.emptyMap());

    /**
     * Retrieve a value if it is present.
     *
     * @param name The name of the value
     * @return An {@link Optional} of the value
     */
    default Optional<V> getFirst(CharSequence name) {
        Optional<List<V>> list = get(name);
        return list.flatMap(v -> {
            if (!v.isEmpty()) {
                return Optional.ofNullable(v.get(0));
            }
            return Optional.empty();
        });
    }

    /**
     * An empty {@link OptionalValues}.
     *
     * @param <T> The generic type
     * @return The empty values
     */
    @SuppressWarnings("unchecked")
    static <T> OptionalMultiValues<T> empty() {
        return EMPTY_VALUES;
    }

    /**
     * Creates a new {@link OptionalValues} for the given type and values.
     *
     * @param values A map of values
     * @param <T>    The target generic type
     * @return The values
     */
    static <T> OptionalMultiValues<T> of(Map<CharSequence, List<T>> values) {
        return new OptionalMultiValuesMap<>(List.class, values);
    }
}
