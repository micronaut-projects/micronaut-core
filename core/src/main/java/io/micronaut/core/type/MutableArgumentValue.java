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
package io.micronaut.core.type;

/**
 * A mutable version of the {@link ArgumentValue} interface.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableArgumentValue<V> extends ArgumentValue<V> {

    /**
     * Sets the argument value.
     *
     * @param value The value
     * @throws IllegalArgumentException If the argument is not a compatible argument
     */
    void setValue(V value);

    /**
     * Create a new {@link MutableArgumentValue} for the given {@link Argument} and value.
     *
     * @param argument The argument
     * @param value    The value
     * @param <T>      The value type
     * @return The created instance
     */
    static <T> MutableArgumentValue<T> create(Argument<T> argument, T value) {
        return new DefaultMutableArgumentValue<>(argument, value);
    }
}
