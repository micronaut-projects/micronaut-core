/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.context.bind;

import io.micronaut.context.BeanContext;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.ExecutableBinder;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Executable;

/**
 * Sub-interface of {@link ExecutableBinder} that binds arguments from a {@link BeanContext}.
 *
 * @since 4.0.0
 */
public interface ExecutableBeanContextBinder extends ExecutableBinder<BeanContext> {

    /**
     * Binds a given {@link Executable} using the given registry and source object.
     *
     * @param target The target executable
     * @param source The bean context
     * @param <T> The executable target type
     * @param <R> The executable return type
     * @return The bound executable
     * @throws UnsatisfiedArgumentException When the executable could not be satisfied
     */
    <T, R> BoundExecutable<T, R> bind(
        Executable<T, R> target,
        BeanContext source
    ) throws UnsatisfiedArgumentException;
}
