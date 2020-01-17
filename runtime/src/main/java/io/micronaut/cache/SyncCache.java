/*
 * Copyright 2017-2019 original authors
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * <p>A synchronous API for accessing cache values that is useful for in-memory caching implementations.</p>
 * <p>
 * <p>Caching implementations that require blocking IO should implement the {@link #getExecutorService()} method to provide an
 * executor service to offload the operations to. If the cache natively supports asynchronous operations, override the {@link #async()} method to provide a more customized asynchronous solution.</p>
 * <p>
 * <p>Implementers of this interface should mark the implementation as {@link io.micronaut.core.annotation.Blocking} if a blocking operation is
 * required to read or write cache values</p>
 *
 * @param <C> The native cache implementation
 *
 * @author Graeme Rocher
 * @see Cache
 * @see AsyncCache
 * @since 1.0
 */
public interface SyncCache<C> extends Cache<C> {

    /**
     * Resolve the given value for the given key.
     *
     * @param key          The cache key
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the value if it exists and is able to be converted to the specified type
     */
    @Nonnull
    <T> Optional<T> get(@Nonnull Object key, @Nonnull Argument<T> requiredType);

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
    <T> T get(@Nonnull Object key, @Nonnull Argument<T> requiredType, @Nonnull Supplier<T> supplier);

    /**
     * <p>Cache the specified value using the specified key if it is not already present.</p>
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @param <T>   The concrete type
     * @return An optional of the existing value or {@link Optional#empty()} if the specified value parameter was cached
     */
    @Nonnull
    <T> Optional<T> putIfAbsent(@Nonnull Object key, @Nonnull T value);

    /**
     * <p>Cache the supplied value using the specified key if it is not already present.</p>
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value supplier to be associated with the specified key
     * @param <T>   The concrete type
     * @return An optional of the existing value or the new value returned by the supplier
     */
    @Nonnull
    default <T> T putIfAbsent(@Nonnull Object key, @Nonnull Supplier<T> value) {
        T val = value.get();
        return putIfAbsent(key, val).orElse(val);
    }

    /**
     * <p>Cache the specified value using the specified key.</p>
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    void put(@Nonnull Object key, @Nonnull Object value);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     */
    void invalidate(@Nonnull Object key);

    /**
     * Invalidate all cached values within this cache.
     */
    void invalidateAll();

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
    default <T> T get(@Nonnull Object key, @Nonnull Class<T> requiredType, @Nonnull Supplier<T> supplier) {
        return get(key, Argument.of(requiredType), supplier);
    }

    /**
     * Resolve the given value for the given key.
     *
     * @param key          The cache key
     * @param requiredType The required type
     * @param <T>          The concrete type
     * @return An optional containing the value if it exists and is able to be converted to the specified type
     */
    @Nonnull
    default <T> Optional<T> get(@Nonnull Object key, @Nonnull Class<T> requiredType) {
        return get(key, Argument.of(requiredType));
    }

    /**
     * @return The executor service used to construct the default
     * asynchronous cache.
     */
    @Nullable
    default ExecutorService getExecutorService() {
        return null;
    }

    /**
     * <p>This method returns an async version of this cache interface implementation.</p>
     * <p>
     * <p>The default behaviour will execute the operations in the same thread if null
     * is returned from {@link #getExecutorService()}. If an executor service is returned, the
     * operations will be offloaded to the provided executor service.</p>
     *
     * @return The {@link AsyncCache} implementation for this cache
     */
    @Nonnull
    default AsyncCache<C> async() {
        ExecutorService executorService = getExecutorService();
        if (executorService == null) {
            return new DelegatingAsyncBlockingCache<>(this);
        } else {
            return new DelegatingAsyncCache<>(this, executorService);
        }
    }
}
