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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * <p>A default {@link SyncCache} implementation based on Caffeine</p>
 * <p>
 * <p>Since Caffeine is a non-blocking in-memory cache the {@link #async()} method will return an implementation that
 * runs operations in the current thread.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachBean(CacheConfiguration.class)
public class DefaultSyncCache implements SyncCache<com.github.benmanes.caffeine.cache.Cache> {

    private final CacheConfiguration cacheConfiguration;
    private final com.github.benmanes.caffeine.cache.Cache cache;
    private final ApplicationContext applicationContext;
    private final ConversionService<?> conversionService;

    /**
     * Construct a sync cache implementation with given configurations.
     *
     * @param cacheConfiguration The cache configurations
     * @param applicationContext The application context
     * @param conversionService To convert the value from the cache into given required type
     */
    public DefaultSyncCache(CacheConfiguration cacheConfiguration, ApplicationContext applicationContext, ConversionService<?> conversionService) {
        this.cacheConfiguration = cacheConfiguration;
        this.applicationContext = applicationContext;
        this.conversionService = conversionService;
        this.cache = buildCache(cacheConfiguration);
    }

    @Override
    public String getName() {
        return cacheConfiguration.getCacheName();
    }

    @Override
    public com.github.benmanes.caffeine.cache.Cache getNativeCache() {
        return cache;
    }

    @Override
    public <T> Optional<T> get(Object key, Argument<T> requiredType) {
        Object value = cache.getIfPresent(key);
        if (value != null) {
            return conversionService.convert(value, ConversionContext.of(requiredType));
        }
        return Optional.empty();
    }

    @Override
    public <T> T get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
        Object value = cache.get(key, o -> supplier.get());
        if (value != null) {
            Optional<T> converted = conversionService.convert(value, ConversionContext.of(requiredType));
            return converted.orElseThrow(() ->
                new IllegalArgumentException("Cache supplier returned a value that cannot be converted to type: " + requiredType.getName())
            );
        }
        return (T) value;
    }

    @Override
    public void invalidate(Object key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            // null is the same as removal
            cache.invalidate(key);
        } else {
            cache.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> putIfAbsent(Object key, T value) {
        Object previous = cache.asMap().putIfAbsent(key, value);
        return Optional.ofNullable((T) previous);
    }

    /**
     * Build a cache from the given configurations.
     *
     * @param cacheConfiguration The cache configurations
     * @return cache
     */
    protected com.github.benmanes.caffeine.cache.Cache buildCache(CacheConfiguration cacheConfiguration) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        cacheConfiguration.getExpireAfterAccess().ifPresent(duration -> builder.expireAfterAccess(duration.toMillis(), TimeUnit.MILLISECONDS));
        cacheConfiguration.getExpireAfterWrite().ifPresent(duration -> builder.expireAfterWrite(duration.toMillis(), TimeUnit.MILLISECONDS));
        cacheConfiguration.getInitialCapacity().ifPresent(builder::initialCapacity);
        cacheConfiguration.getMaximumSize().ifPresent(builder::maximumSize);
        cacheConfiguration.getMaximumWeight().ifPresent((long weight) -> {
            builder.maximumWeight(weight);
            builder.weigher(findWeigher());
        });

        if (cacheConfiguration.isTestMode()) {
            // run commands on same thread
            builder.executor(Runnable::run);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Weigher<Object, Object> findWeigher() {
        return applicationContext.findBean(Weigher.class, Qualifiers.byName(cacheConfiguration.getCacheName()))
                .orElseGet(() -> applicationContext.findBean(Weigher.class)
                        .orElse(Weigher.singletonWeigher()));
    }
}
