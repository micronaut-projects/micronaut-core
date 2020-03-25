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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;

import java.util.Collections;
import java.util.List;

/**
 * A bound {@link Executable} is an executable who argument values have been pre-bound to
 * values using a {@link ArgumentBinderRegistry}.
 *
 * Unlike a normal {@link Executable} zero arguments are expected and an exception will
 * be thrown if the underlying target {@link Executable} cannot be invoked with the current state
 *
 * @param <T>
 * @param <R>
 */
public interface BoundExecutable<T, R> extends Executable<T, R> {

    /**
     * @return The target executable
     */
    Executable<T, R> getTarget();

    /**
     * Invoke the bound {@link Executable}.
     *
     * @param instance The target instance
     * @return The result
     */
    R invoke(T instance);

    /**
     * That arguments that will be used to invoke the method.
     *
     * @return The arguments
     */
    Object[] getBoundArguments();

    /**
     * If the executable can only be partially bound then this method will return the arguments that have not been bound.
     *
     * @return The unbound arguments
     */
    default List<Argument<?>> getUnboundArguments() {
        return Collections.emptyList();
    }

    @Override
    default R invoke(T instance, Object... arguments) {
        return getTarget().invoke(instance, arguments);
    }

    @Override
    default Argument[] getArguments() {
        return getTarget().getArguments();
    }

    @Override
    default AnnotationMetadata getAnnotationMetadata() {
        return getTarget().getAnnotationMetadata();
    }
}
