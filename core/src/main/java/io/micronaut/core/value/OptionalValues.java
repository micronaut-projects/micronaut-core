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
package io.micronaut.core.value;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * A simple type safe abstraction over a map of optional values.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public interface OptionalValues<V> extends Iterable<CharSequence> {

    /**
     * Constant for empty values.
     */
    OptionalValues EMPTY_VALUES = of(Object.class, Collections.emptyMap());

    /**
     * Retrieve a value if it is present.
     *
     * @param name The name of the value
     * @return An {@link Optional} of the value
     */
    Optional<V> get(CharSequence name);

    /**
     * @return The values
     */
    Collection<V> values();

    /**
     * @return Whether the {@link OptionalValues} is empty
     */
    default boolean isEmpty() {
        return values().isEmpty();
    }

    /**
     * Performs the given action for each entry in this {@link OptionalValues} until all entries
     * have been processed or the action throws an exception.   Unless
     * otherwise specified by the implementing class, actions are performed in
     * the order of entry set iteration (if an iteration order is specified.)
     * Exceptions thrown by the action are relayed to the caller.
     *
     * @param action The action to be performed for each entry
     * @throws NullPointerException if the specified action is null
     *                              removed during iteration
     */
    default void forEach(BiConsumer<CharSequence, ? super V> action) {
        Objects.requireNonNull(action);
        for (CharSequence k : this) {
            get(k).ifPresent(v ->
                action.accept(k, v)
            );
        }
    }

    /**
     * An empty {@link OptionalValues}.
     *
     * @param <T> The generic type
     * @return The empty values
     */
    static <T> OptionalValues<T> empty() {
        return EMPTY_VALUES;
    }

    /**
     * Creates a new {@link OptionalValues} for the given type and values.
     *
     * @param type   The target type
     * @param values A map of values
     * @param <T>    The target generic type
     * @return The values
     */
    static <T> OptionalValues<T> of(Class<T> type, @Nullable Map<CharSequence, ?> values) {
        if (values == null) {
            return empty();
        }
        return new OptionalValuesMap<>(type, values);
    }
}
