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
package io.micronaut.cache;

import io.micronaut.core.type.Argument;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * <p>A cache implementation that supports async non-blocking caching operations.</p>
 *
 * @param <C> The native cache implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AsyncCache<C> extends Cache<C> {

    /**
     * Resolve the given value for the given key.
     *
     * @param key          The cache key
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the value if it exists and is able to be converted to the specified type
     */
    <T> CompletableFuture<Optional<T>> get(Object key, Argument<T> requiredType);

    /**
     * Resolve the given value for the given key. If the value is not found the specified {@link Supplier} will
     * be invoked and the return value cached.
     *
     * @param key          The cache key
     * @param requiredType The required type
     * @param supplier     The supplier that should be invoked if the value is not found
     * @param <T>          The concrete type
     * @return An optional containing the value if it exists and is able to be converted to the specified type
     */
    <T> CompletableFuture<T> get(Object key, Argument<T> requiredType, Supplier<T> supplier);

    /**
     * <p>Cache the specified value using the specified key if it is not already present.</p>
     *
     * @param key   The key with which the specified value is to be associated
     * @param value The value to be associated with the specified key
     * @param <T> The concrete type
     * @return An optional of the existing value or {@link Optional#empty()} if the specified value parameter was cached
     */
    <T> CompletableFuture<Optional<T>> putIfAbsent(Object key, T value);

    /**
     * <p>Cache the specified value using the specified key.</p>
     *
     * @param key   The key with which the specified value is to be associated
     * @param value The value to be associated with the specified key
     * @return A future with a boolean indicating whether the operation was successful or not
     */
    CompletableFuture<Boolean> put(Object key, Object value);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     * @return A future with a boolean indicating whether the operation was succesful or not
     */
    CompletableFuture<Boolean> invalidate(Object key);

    /**
     * Invalidate all cached values within this cache.
     *
     * @return A future with a boolean indicating whether the operation was succesful or not
     */
    CompletableFuture<Boolean> invalidateAll();

    /**
     * Resolve the given value for the given key.
     *
     * @param key          The cache key
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the value if it exists and is able to be converted to the specified type
     */
    default <T> CompletableFuture<Optional<T>> get(Object key, Class<T> requiredType) {
        return get(key, Argument.of(requiredType));
    }

    /**
     * Resolve the given value for the given key. If the value is not found the specified {@link Supplier} will
     * be invoked and the return value cached.
     *
     * @param key          The cache key
     * @param requiredType The required type
     * @param supplier     The supplier that should be invoked if the value is not found
     * @param <T>          The concrete type
     * @return An optional containing the value if it exists and is able to be converted to the specified type
     */
    default <T> CompletableFuture<T> get(Object key, Class<T> requiredType, Supplier<T> supplier) {
        return get(key, Argument.of(requiredType), supplier);
    }
}
