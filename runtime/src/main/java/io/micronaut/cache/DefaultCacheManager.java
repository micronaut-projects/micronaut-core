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

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.ArrayUtils;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of the {@link CacheManager} interface.
 *
 * @param <C> The native cache implementation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class DefaultCacheManager<C> implements CacheManager<C> {
    private final Map<String, SyncCache<C>> cacheMap;

    /**
     * Create default cache manager for the given caches.
     *
     * @param caches List of synchronous cache implementations
     */
    public DefaultCacheManager(SyncCache<C>... caches) {
        if (ArrayUtils.isEmpty(caches)) {
            this.cacheMap = Collections.emptyMap();
        } else {
            this.cacheMap = new LinkedHashMap<>(caches.length);
            for (SyncCache<C> cache : caches) {
                this.cacheMap.put(cache.getName(), cache);
            }
        }
    }

    @Override
    public Set<String> getCacheNames() {
        return cacheMap.keySet();
    }

    @Override
    public SyncCache<C> getCache(String name) {
        SyncCache<C> cache = cacheMap.get(name);
        if (cache == null) {
            throw new ConfigurationException("No cache configured for name: " + name);
        }
        return cache;
    }
}
