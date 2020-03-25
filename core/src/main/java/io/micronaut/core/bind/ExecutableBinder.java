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

import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Executable;

/**
 * <p>An ExecutableBinder is capable of taking a target {@link Executable} and fulfilling the argument
 * requirements using the provided binding source and {@link ArgumentBinderRegistry}</p>
 *
 * <p>The returned {@link BoundExecutable} will have all of the required arguments bound and
 * can then be called simply by calling invoke.</p>
 *
 * <p>If an argument could not be bound then an exception will be</p>
 *
 * @param <S> The source type
 */
public interface ExecutableBinder<S> {

    /**
     * Binds a given {@link Executable} using the given registry and source object.
     *
     * @param target The target executable
     * @param registry The registry to use
     * @param source The binding source
     * @param <T> The executable target type
     * @param <R> The executable return type
     * @return The bound executable
     * @throws UnsatisfiedArgumentException When the executable could not be satisfied
     */
    <T, R> BoundExecutable<T, R> bind(
            Executable<T, R> target,
            ArgumentBinderRegistry<S> registry,
            S source
    ) throws UnsatisfiedArgumentException;

    /**
     * Binds a given {@link Executable} using the given registry and source object. Unlike {@link #bind(Executable, ArgumentBinderRegistry, Object)} this
     * method will not throw an {@link UnsatisfiedArgumentException} if an argument cannot be bound. Instead the {@link BoundExecutable#getUnboundArguments()} property
     * will be populated with any arguments that could not be bound
     *
     * @param target The target executable
     * @param registry The registry to use
     * @param source The binding source
     * @param <T> The executable target type
     * @param <R> The executable return type
     * @return The bound executable
     */
    <T, R> BoundExecutable<T, R> tryBind(
            Executable<T, R> target,
            ArgumentBinderRegistry<S> registry,
            S source
    );
}
