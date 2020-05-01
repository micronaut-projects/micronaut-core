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
package io.micronaut.cache.jcache;

import io.micronaut.cache.DefaultCacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.TaskExecutors;

import javax.annotation.Nonnull;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Adapter for JCache. Replaces the {@link DefaultCacheManager} if a JCache bean is present.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Replaces(DefaultCacheManager.class)
@Requires(beans = CacheManager.class)
@Requires(property = JCacheManager.JCACHE_ENABLED, value = "true", defaultValue = "true")
@Primary
public class JCacheManager implements io.micronaut.cache.CacheManager<Cache> {

    /**
     * Whether JCache integration is enabled.
     */
    public static final String JCACHE_ENABLED = "micronaut.jcache.enabled";

    private final CacheManager cacheManager;
    private final ConversionService<?> conversionService;
    private final ExecutorService executorService;
    private final JCacheConfiguration cacheConfiguration;

    /**
     * Default constructor.
     *
     * @param cacheManager The cache manager
     * @param executorService The executor to execute I/O operations
     * @param conversionService The conversion service
     * @param cacheConfiguration The cache configuration
     */
    @Inject
    protected JCacheManager(
            @Nonnull CacheManager cacheManager,
            @Nonnull @Named(TaskExecutors.IO) ExecutorService executorService,
            @Nonnull ConversionService<?> conversionService,
            @Nonnull JCacheConfiguration cacheConfiguration) {
        this.cacheManager = cacheManager;
        this.conversionService = conversionService;
        this.executorService = executorService;
        this.cacheConfiguration = cacheConfiguration;
    }

    /**
     * Default constructor.
     *
     * @param cacheManager The cache manager
     * @param executorService The executor to execute I/O operations
     * @param conversionService The conversion service
     */
    protected JCacheManager(
            @Nonnull CacheManager cacheManager,
            @Nonnull @Named(TaskExecutors.IO) ExecutorService executorService,
            @Nonnull ConversionService<?> conversionService) {
        this.cacheManager = cacheManager;
        this.conversionService = conversionService;
        this.executorService = executorService;
        this.cacheConfiguration = new JCacheConfiguration();
    }

    @Override
    @Nonnull
    public Set<String> getCacheNames() {
        return CollectionUtils.iterableToSet(cacheManager.getCacheNames());
    }

    @Override
    @Nonnull
    public SyncCache<Cache> getCache(String name) {
        final Cache<Object, Object> cache = cacheManager.getCache(name);
        if (cache == null) {
            throw new ConfigurationException("No cache configured for name: " + name);
        }
        return new JCacheSyncCache(cache, cacheConfiguration.isConvert() ? conversionService : null, executorService);
    }

    /**
     * @return The JCache cache manager.
     */
    @Nonnull
    public CacheManager getCacheManager() {
        return cacheManager;
    }
}
