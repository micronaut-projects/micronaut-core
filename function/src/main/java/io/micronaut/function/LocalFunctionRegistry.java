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
package io.micronaut.function;

import io.micronaut.inject.ExecutableMethod;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * <p>A registry of defined {@link FunctionBean} instances containing within the current running application.</p>
 * <p>
 * <p>This interface is designed to allow the location and interaction with non-remote functions</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface LocalFunctionRegistry {

    /**
     * The name of the default function name.
     */
    String FUNCTION_NAME = "micronaut.function.name";

    /**
     * The name of the default function name.
     */
    String FUNCTION_CHARSET = "micronaut.function.charset";

    /**
     * Prefix used to identify function names.
     */
    String FUNCTION_PREFIX = "function:";

    /**
     * A map of available functions with the key being the function name and the value being the function URI.
     *
     * @return A map of functions
     */
    Map<String, URI> getAvailableFunctions();

    /**
     * Find the first available registered function.
     * @param <T> The declaring type
     * @param <R> The result of the method call
     * @return The {@link ExecutableMethod} method representing the function
     */
    <T, R> Optional<? extends ExecutableMethod<T, R>> findFirst();

    /**
     * Find the first available registered function.
     * @param <T> The declaring type
     * @param <R> The result of the method call
     * @param name the name
     * @return The {@link ExecutableMethod} method representing the function
     */
    <T, R> Optional<? extends ExecutableMethod<T, R>> find(String name);

    /**
     * Find a {@link Supplier} for the given name.
     *
     * @param name The name
     * @param <T>  The type
     * @return An {@link Optional} of a {@link Supplier}
     */
    <T> Optional<ExecutableMethod<Supplier<T>, T>> findSupplier(String name);

    /**
     * Find a {@link Consumer} for the given name.
     *
     * @param name The name
     * @param <T>  The type
     * @return An {@link Optional} of a {@link Consumer}
     */
    <T> Optional<ExecutableMethod<Consumer<T>, Void>> findConsumer(String name);

    /**
     * Find a {@link java.util.function.Function} for the given name.
     *
     * @param name The name
     * @param <T>  The type
     * @param <R> The result of the method call
     * @return An {@link Optional} of a {@link java.util.function.Function}
     */
    <T, R> Optional<ExecutableMethod<java.util.function.Function<T, R>, R>> findFunction(String name);

    /**
     * Find a {@link java.util.function.BiFunction} for the given name.
     *
     * @param name The name
     * @param <T>  The type
     * @param <U> the type of the second argument to the function
     * @param <R> The result of the method call
     * @return An {@link Optional} of a {@link java.util.function.BiFunction}
     */
    <T, U, R> Optional<ExecutableMethod<java.util.function.BiFunction<T, U, R>, R>> findBiFunction(String name);
}
