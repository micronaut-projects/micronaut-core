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
package io.micronaut.cache.jcache;

import io.micronaut.cache.AsyncCache;
import io.micronaut.cache.SyncCache;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import javax.cache.Cache;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * An implementation of {@link SyncCache} for JCache.
 *
 * @author graemerocher
 * @since 1.1.0
 */
class JCacheSyncCache implements SyncCache<Cache> {

    private final Cache nativeCache;
    private final ConversionService<?> conversionService;
    private final AsyncCache<Cache> asyncCache;

    /**
     * Default constructor.
     *
     * @param nativeCache The native cache
     * @param conversionService The conversion service
     * @param ioExecutor The IO executor
     */
    JCacheSyncCache(
            @Nonnull Cache<?, ?> nativeCache,
            ConversionService<?> conversionService,
            ExecutorService ioExecutor) {
        ArgumentUtils.requireNonNull("nativeCache", nativeCache);
        this.nativeCache = nativeCache;
        this.conversionService = conversionService;
        asyncCache = new AsyncCache<Cache>() {
            @Override
            public <T> CompletableFuture<Optional<T>> get(Object key, Argument<T> requiredType) {
                return CompletableFuture.supplyAsync(() -> JCacheSyncCache.this.get(key, requiredType), ioExecutor);
            }

            @Override
            public <T> CompletableFuture<T> get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
                return CompletableFuture.supplyAsync(() -> JCacheSyncCache.this.get(key, requiredType, supplier), ioExecutor);
            }

            @Override
            public <T> CompletableFuture<Optional<T>> putIfAbsent(Object key, T value) {
                return CompletableFuture.supplyAsync(() -> JCacheSyncCache.this.putIfAbsent(key, value), ioExecutor);
            }

            @SuppressWarnings("unchecked")
            @Override
            public CompletableFuture<Boolean> put(Object key, Object value) {
                return CompletableFuture.supplyAsync(() -> {
                            JCacheSyncCache.this.nativeCache.put(key, value);
                            return true;
                        },
                        ioExecutor
                );
            }

            @Override
            public CompletableFuture<Boolean> invalidate(Object key) {
                return CompletableFuture.supplyAsync(() -> {
                            JCacheSyncCache.this.invalidate(key);
                            return true;
                        },
                        ioExecutor
                );
            }

            @Override
            public CompletableFuture<Boolean> invalidateAll() {
                return CompletableFuture.supplyAsync(() -> {
                            JCacheSyncCache.this.invalidateAll();
                            return true;
                        },
                        ioExecutor
                );
            }

            @Override
            public String getName() {
                return JCacheSyncCache.this.getName();
            }

            @Override
            public Cache getNativeCache() {
                return JCacheSyncCache.this.nativeCache;
            }
        };
    }

    @Override
    public <T> Optional<T> get(Object key, Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("key", key);
        final Object v = nativeCache.get(key);
        if (v != null) {
            return conversionService.convert(v, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public <T> T get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
        ArgumentUtils.requireNonNull("key", key);
        return get(key, requiredType).orElseGet(supplier);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> putIfAbsent(Object key, T value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        final T v = (T) nativeCache.getAndReplace(key, value);
        final Class<T> aClass = (Class<T>) value.getClass();
        return conversionService.convert(v, aClass);
    }

    @Override
    public void put(Object key, Object value) {
        ArgumentUtils.requireNonNull("key", key);
        if (value != null) {
            nativeCache.put(key, value);
        }
    }

    @Override
    public void invalidate(Object key) {
        ArgumentUtils.requireNonNull("key", key);
        nativeCache.remove(key);
    }

    @Override
    public void invalidateAll() {
        nativeCache.clear();
    }

    @Override
    public String getName() {
        return nativeCache.getName();
    }

    @Override
    public Cache<?, ?> getNativeCache() {
        return nativeCache;
    }

    @Override
    public AsyncCache<Cache> async() {
        return asyncCache;
    }
}
