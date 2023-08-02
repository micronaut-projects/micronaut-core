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

import io.micronaut.core.annotation.Internal;

/**
 * A variation of {@link Executable} that exposes invoke method without arguments validation.
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface UnsafeExecutable<T, R> extends Executable<T, R> {

    /**
     * Invokes the method without the arguments' validation.
     *
     * @param instance  The instance. Nullable only if it's a static method call.
     * @param arguments The arguments
     * @return The result
     */
    R invokeUnsafe(T instance, Object... arguments);
}
