/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Collects a set of executable methods {@link ExecutableMethod}.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public interface ExecutableMethodsDefinition<T> {

    /**
     * Finds a single {@link ExecutableMethod} for the given name and argument types.
     *
     * @param name          The method name
     * @param argumentTypes The argument types
     * @param <R>           The return type
     * @return An optional {@link ExecutableMethod}
     */
    @NonNull
    <R> Optional<ExecutableMethod<T, R>> findMethod(@NonNull String name, @NonNull Class<?>... argumentTypes);

    /**
     * Finds possible methods for the given method name.
     *
     * @param name The method name
     * @param <R>  The return type
     * @return The possible methods
     */
    @NonNull
    <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(@NonNull String name);


    /**
     * @return The {@link ExecutableMethod} instances for this definition
     */
    @NonNull
    Collection<ExecutableMethod<T, ?>> getExecutableMethods();

}
