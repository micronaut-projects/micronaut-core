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
package io.micronaut.management.endpoint.caches.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.caches.CacheData;
import io.micronaut.management.endpoint.caches.CachesEndpoint;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Default cache data implementation.
 *
 * @author Marcel Overdijk
 * @since 1.1
 */
@Singleton
@Requires(beans = CachesEndpoint.class)
public class DefaultCacheData implements CacheData<Map<String, Object>> {

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getData(SyncCache cache) {

        Map<String, Object> values = new LinkedHashMap<>(2);

        values.put("implementationClass", cache.getNativeCache().getClass().getName());

        if (cache.getNativeCache() instanceof Cache) {
            values.put("caffeine", getCaffeineCacheData((Cache) cache.getNativeCache()));
        }

        return values;
    }

    private Map<String, Object> getCaffeineCacheData(Cache caffeineCache) {

        Policy policy = caffeineCache.policy();
        Policy.Eviction eviction = (Policy.Eviction) policy.eviction().orElse(null);
        Policy.Expiration expireAfterAccess = (Policy.Expiration) policy.expireAfterAccess().orElse(null);
        Policy.Expiration expireAfterWrite = (Policy.Expiration) policy.expireAfterWrite().orElse(null);
        Long maximumSize = (!eviction.isWeighted() ? eviction.getMaximum() : null);
        Long maximumWeight = (eviction.isWeighted() ? eviction.getMaximum() : null);
        Long weightedSize = (eviction.weightedSize().isPresent() ? eviction.weightedSize().getAsLong() : null);
        boolean isRecordingStats = policy.isRecordingStats();

        Map<String, Object> values = new LinkedHashMap<>(8);

        values.put("estimatedSize", caffeineCache.estimatedSize());
        values.put("maximumSize", maximumSize);
        values.put("maximumWeight", maximumWeight);
        values.put("weightedSize", weightedSize);
        values.put("expireAfterAccess", getExpiresAfter(expireAfterAccess));
        values.put("expireAfterWrite", getExpiresAfter(expireAfterWrite));
        values.put("recordingStats", isRecordingStats);

        if (isRecordingStats) {
            values.put("stats", getStatsData(caffeineCache.stats()));
        }

        return values;
    }

    private Long getExpiresAfter(Policy.Expiration expiration) {
        return expiration != null ? expiration.getExpiresAfter(TimeUnit.MILLISECONDS) : null;
    }

    private Map<String, Object> getStatsData(CacheStats stats) {

        Map<String, Object> values = new LinkedHashMap<>(13);

        values.put("requestCount", stats.requestCount());
        values.put("hitCount", stats.hitCount());
        values.put("hitRate", stats.hitRate());
        values.put("missCount", stats.missCount());
        values.put("missRate", stats.missRate());
        values.put("evictionCount", stats.evictionCount());
        values.put("evictionWeight", stats.evictionWeight());

        return values;
    }
}
