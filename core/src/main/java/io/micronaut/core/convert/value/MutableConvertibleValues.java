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
package io.micronaut.core.convert.value;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

/**
 * A {@link ConvertibleValues} that is mutable.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableConvertibleValues<V> extends ConvertibleValues<V> {

    /**
     * Insert a value for the given key and value.
     *
     * @param key   The key
     * @param value The value
     * @return The previous value
     */
    MutableConvertibleValues<V> put(CharSequence key, @Nullable V value);

    /**
     * Remove a value for the given key.
     *
     * @param key The key
     * @return The previous value
     */
    MutableConvertibleValues<V> remove(CharSequence key);

    /**
     * Clear all values.
     *
     * @return This values instance
     */
    MutableConvertibleValues<V> clear();

    /**
     * Put all the values from the given map into this values instance.
     *
     * @param values The values
     * @return This values instance
     */
    default MutableConvertibleValues<V> putAll(Map<CharSequence, V> values) {
        if (values != null) {
            for (Map.Entry<CharSequence, V> entry : values.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * Put all the values from the given values into this values instance.
     *
     * @param values The values
     * @return This values instance
     */
    default MutableConvertibleValues<V> putAll(ConvertibleValues<V> values) {
        if (values != null) {
            for (Map.Entry<String, V> entry : values) {
                put(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * Creates a new {@link ConvertibleValues} for the values.
     *
     * @param values A map of values
     * @param <T>    The target generic type
     * @return The values
     */
    static <T> MutableConvertibleValues<T> of(Map<? extends CharSequence, T> values) {
        if (values == null) {
            return new MutableConvertibleValuesMap<>();
        } else {
            return new MutableConvertibleValuesMap<>(values);
        }
    }
}
