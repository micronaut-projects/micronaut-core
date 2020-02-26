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

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Abstract {@link SyncCache} implementation that relies on a cache object that implements the {@link Map} interface.
 *
 * @param <C> the native cache object, such that <code>C extends Map</code>.
 * @author Álvaro Sánchez-Mariscal
 * @since 1.3.0
 */
public abstract class AbstractMapBasedSyncCache<C extends Map<Object, Object>> implements SyncCache<C> {

    private final ConversionService<?> conversionService;
    private final C nativeCache;

    /**
     * @param conversionService the conversion service
     * @param nativeCache the native cache
     */
    public AbstractMapBasedSyncCache(ConversionService<?> conversionService, C nativeCache) {
        this.conversionService = conversionService;
        this.nativeCache = nativeCache;
    }

    /**
     * @return The conversion service
     */
    public ConversionService<?> getConversionService() {
        return conversionService;
    }

    @NonNull
    @Override
    public <T> Optional<T> get(@NonNull Object key, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("key", key);
        Object value = nativeCache.get(key);
        if (value != null) {
            return conversionService.convert(value, ConversionContext.of(requiredType));
        }
        return Optional.empty();
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Argument<T> requiredType, @NonNull Supplier<T> supplier) {
        ArgumentUtils.requireNonNull("key", key);
        Optional<T> existingValue = get(key, requiredType);
        if (existingValue.isPresent()) {
            return existingValue.get();
        } else {
            T value = supplier.get();
            put(key, value);
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T> Optional<T> putIfAbsent(@NonNull Object key, @NonNull T value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        final T v = (T) nativeCache.putIfAbsent(key, value);
        final Class<T> aClass = (Class<T>) value.getClass();
        return conversionService.convert(v, aClass);
    }

    @NonNull
    @Override
    public <T> T putIfAbsent(@NonNull Object key, @NonNull Supplier<T> value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        final Object v = nativeCache.get(key);
        if (v == null) {
            return (T) nativeCache.put(key, value.get());
        } else {
            return (T) v;
        }
    }

    @Override
    public void put(@NonNull Object key, @NonNull Object value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        nativeCache.put(key, value);
    }

    @Override
    public void invalidate(@NonNull Object key) {
        ArgumentUtils.requireNonNull("key", key);
        nativeCache.remove(key);
    }

    @Override
    public void invalidateAll() {
        nativeCache.clear();
    }

    @Override
    public abstract String getName();

    @Override
    public C getNativeCache() {
        return nativeCache;
    }

}
