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

import io.micronaut.core.type.Argument;

import java.util.Optional;

/**
 * A registry of {@link ArgumentBinder} instances.
 * @param <S> type Generic
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ArgumentBinderRegistry<S> {

    /**
     * Adds a request argument binder to the registry.
     * @param binder The binder
     * @param <T> The argument type
     * @param <ST> The source type
     * @since 2.0
     */
    default <T, ST> void addRequestArgumentBinder(ArgumentBinder<T, ST> binder) {
        throw new UnsupportedOperationException("Binder registry is not mutable");
    }

    /**
     * Locate an {@link ArgumentBinder} for the given argument and source type.
     *
     * @param argument The argument
     * @param source   The source
     * @param <T>      The argument type
     * @return An {@link Optional} of {@link ArgumentBinder}
     */
    <T> Optional<ArgumentBinder<T, S>> findArgumentBinder(Argument<T> argument, S source);
}
