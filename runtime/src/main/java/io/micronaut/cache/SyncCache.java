/*
 * Copyright 2017-2018 original authors
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
 * <p>A synchronous API for accessing cache values that is useful for in-memory caching implementations.</p>
 * <p>
 * <p>Caching implementations that require blocking IO should implement the {@link #async()} method to provide a
 * non-blocking implementation of this interface</p>
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
    <T> Optional<T> get(Object key, Argument<T> requiredType);

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
    <T> T get(Object key, Argument<T> requiredType, Supplier<T> supplier);

    /**
     * <p>Cache the specified value using the specified key if it is not already present.</p>
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @param <T>   The concrete type
     * @return An optional of the existing value or {@link Optional#empty()} if the specified value parameter was cached
     */
    <T> Optional<T> putIfAbsent(Object key, T value);

    /**
     * <p>Cache the specified value using the specified key.</p>
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    void put(Object key, Object value);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     */
    void invalidate(Object key);

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
    default <T> T get(Object key, Class<T> requiredType, Supplier<T> supplier) {
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
    default <T> Optional<T> get(Object key, Class<T> requiredType) {
        return get(key, Argument.of(requiredType));
    }

    /**
     * <p>This method should return an async API version of this cache interface implementation.</p>
     * <p>
     * <p>The default behaviour assumes the cache implementation is running in-memory and performs no blocking
     * operations and hence simply delegates to the {@link SyncCache} implementation.
     * If I/O operations are required implementors should override this API and provide an API that implements
     * {@link AsyncCache} in a non-blocking manner.</p>
     *
     * @return The {@link AsyncCache} implementation for this cache
     */
    default AsyncCache<C> async() {
        return new AsyncCache<C>() {
            @Override
            public <T> CompletableFuture<Optional<T>> get(Object key, Argument<T> requiredType) {
                try {
                    return CompletableFuture.completedFuture(SyncCache.this.get(key, requiredType));
                } catch (Exception e) {
                    return handleException(e);
                }
            }

            @Override
            public <T> CompletableFuture<T> get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
                try {
                    return CompletableFuture.completedFuture(SyncCache.this.get(key, requiredType, supplier));
                } catch (Exception e) {
                    return handleException(e);
                }
            }

            @Override
            public <T> CompletableFuture<Optional<T>> putIfAbsent(Object key, T value) {
                try {
                    return CompletableFuture.completedFuture(SyncCache.this.putIfAbsent(key, value));
                } catch (Exception e) {
                    return handleException(e);
                }
            }

            @Override
            public String getName() {
                return SyncCache.this.getName();
            }

            @Override
            public C getNativeCache() {
                return SyncCache.this.getNativeCache();
            }

            @Override
            public CompletableFuture<Boolean> put(Object key, Object value) {
                try {
                    SyncCache.this.put(key, value);
                    return CompletableFuture.completedFuture(true);
                } catch (Exception e) {
                    return handleException(e);
                }
            }

            @Override
            public CompletableFuture<Boolean> invalidate(Object key) {
                try {
                    SyncCache.this.invalidate(key);
                    return CompletableFuture.completedFuture(true);
                } catch (Exception e) {
                    return handleException(e);
                }
            }

            @Override
            public CompletableFuture<Boolean> invalidateAll() {
                try {
                    SyncCache.this.invalidateAll();
                    return CompletableFuture.completedFuture(true);
                } catch (Exception e) {
                    return handleException(e);
                }
            }

            private <T> CompletableFuture<T> handleException(Exception e) {
                CompletableFuture<T> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        };
    }
}
