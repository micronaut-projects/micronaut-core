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

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.util.ArgumentUtils;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * <p>Represents an executable reference. The reference could be implemented via reflection (slow) or via generated
 * code</p>.
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Executable<T, R> extends AnnotationMetadataProvider {

    /**
     * @return The declaring type
     * @since 3.0.0
     */
    Class<T> getDeclaringType();

    /**
     * The required argument types.
     *
     * @return The arguments
     */
    Argument<?>[] getArguments();

    /**
     * Finds the index of the argument with the given name.
     *
     * @param name The argument name
     * @return The index into {@link #getArguments()}, or {@code -1} if there is no such argument.
     * Argument names are expected to be unique; if they are not, implementations may throw
     * {@link IllegalArgumentException}. This default returns the first match.
     * @throws NullPointerException if {@code name} is null
     * @since 5.3.0
     */
    default int argumentIndexOf(String name) {
        ArgumentUtils.requireNonNull("name", name);
        Argument<?>[] arguments = getArguments();
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the argument with the given name.
     *
     * @param name The argument name
     * @return The argument, or an empty {@link Optional} if there is no such argument
     * @throws NullPointerException if {@code name} is null
     * @see #argumentIndexOf(String)
     * @since 5.3.0
     */
    default Optional<Argument<?>> getArgument(String name) {
        int index = argumentIndexOf(name);
        return index == -1 ? Optional.empty() : Optional.of(getArguments()[index]);
    }

    /**
     * Invokes the method.
     *
     * @param instance  The instance. Nullable only if it's a static method call.
     * @param arguments The arguments
     * @return The result
     */
    @Nullable R invoke(T instance, @Nullable Object... arguments);
}
