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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * An asynchronous cache that delegates blocking cache operations
 * to the provided executor.
 *
 * @param <C> The cache type
 * @author James Kleeh
 * @since 1.3.0
 */
@Internal
public class DelegatingAsyncCache<C> implements AsyncCache<C> {

    private final SyncCache<C> delegate;
    private final ExecutorService executorService;

    /**
     * @param delegate The delegate blocking cache
     * @param executorService The executor service to run the blocking operations on
     */
    public DelegatingAsyncCache(SyncCache<C> delegate, ExecutorService executorService) {
        this.delegate = delegate;
        this.executorService = executorService;
    }

    @Override
    public <T> CompletableFuture<Optional<T>> get(Object key, Argument<T> requiredType) {
        return CompletableFuture.supplyAsync(() -> delegate.get(key, requiredType), executorService);
    }

    @Override
    public <T> CompletableFuture<T> get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> delegate.get(key, requiredType, supplier), executorService);
    }

    @Override
    public <T> CompletableFuture<Optional<T>> putIfAbsent(Object key, T value) {
        return CompletableFuture.supplyAsync(() -> delegate.putIfAbsent(key, value), executorService);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public C getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public CompletableFuture<Boolean> put(Object key, Object value) {
        return CompletableFuture.supplyAsync(() -> {
            delegate.put(key, value);
            return true;
        }, executorService);
    }

    @Override
    public CompletableFuture<Boolean> invalidate(Object key) {
        return CompletableFuture.supplyAsync(() -> {
            delegate.invalidate(key);
            return true;
        }, executorService);
    }

    @Override
    public CompletableFuture<Boolean> invalidateAll() {
        return CompletableFuture.supplyAsync(() -> {
            delegate.invalidateAll();
            return true;
        }, executorService);
    }
}
