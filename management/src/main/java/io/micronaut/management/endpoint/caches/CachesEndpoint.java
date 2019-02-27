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

package io.micronaut.management.endpoint.caches;

import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import javax.validation.constraints.NotBlank;

/**
 * Exposes an {@link Endpoint} to manage caches.
 *
 * @author Marcel Overdijk
 * @since 1.1.0
 */
@Endpoint(id = CachesEndpoint.NAME, defaultEnabled = false)
public class CachesEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "caches";

    private final CacheManager<?> cacheManager;
    private final CacheDataCollector cacheDataCollector;

    /**
     * @param cacheManager       The {@link CacheManager}
     * @param cacheDataCollector The {@link CacheDataCollector}
     */
    public CachesEndpoint(CacheManager cacheManager,
                          CacheDataCollector cacheDataCollector) {
        this.cacheManager = cacheManager;
        this.cacheDataCollector = cacheDataCollector;
    }

    /**
     * Returns the caches as a {@link Single}.
     *
     * @return The caches as a {@link Single}
     */
    @Read
    public Single getCaches() {
        return Single.fromPublisher(cacheDataCollector.getData(getSyncCaches()));
    }

    /**
     * Returns the cache as a {@link Maybe}.
     *
     * @param name The name of the cache to retrieve
     * @return The cache as a {@link Single}
     */
    @Read
    public Maybe getCache(@NotBlank @Selector String name) {
        return getSyncCache(name).map(cacheDataCollector::getData);
    }

    /**
     * Invalidates all the caches.
     */
    @Delete
    public void invalidateCaches() {
        //noinspection ResultOfMethodCallIgnored
        getSyncCaches().forEach(SyncCache::invalidateAll);
    }

    /**
     * Invalidates the cache.
     *
     * @param name The name of the cache to invalidate
     */
    @Delete
    public void invalidateCache(@NotBlank @Selector String name) {
        //noinspection ResultOfMethodCallIgnored
        getSyncCache(name).subscribe(SyncCache::invalidateAll);
    }

    private Flowable<SyncCache> getSyncCaches() {
        return getCacheNames()
                .map(cacheManager::getCache);
    }

    private Maybe<? extends SyncCache<?>> getSyncCache(String name) {
        return getCacheNames()
                .filter(name::equals)
                .map(cacheManager::getCache)
                .firstElement();
    }

    private Flowable<String> getCacheNames() {
        return Flowable.fromIterable(cacheManager.getCacheNames())
                .sorted();
    }
}
