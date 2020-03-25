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
package io.micronaut.management.endpoint.caches;

import io.micronaut.cache.Cache;
import io.micronaut.cache.CacheInfo;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exposes an {@link Endpoint} to manage caches.
 *
 * @author Marcel Overdijk
 * @author graemerocher
 * @since 1.1.0
 */
@Endpoint(id = CachesEndpoint.NAME, defaultEnabled = false)
public class CachesEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "caches";

    private final CacheManager<Object> cacheManager;

    /**
     * @param cacheManager       The {@link CacheManager}
     */
    public CachesEndpoint(CacheManager<Object> cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Returns the caches as a {@link Single}.
     *
     * @return The caches as a {@link Single}
     */
    @Read
    public Single<Map<String, Object>> getCaches() {
        return Flowable.fromIterable(cacheManager.getCacheNames())
                       .flatMapMaybe(n -> Flowable.fromPublisher(cacheManager.getCache(n).getCacheInfo()).firstElement())
                       .reduce(new HashMap<>(), (seed, info) -> {
                           seed.put(info.getName(), info.get());
                           return seed;
                       }).map(objectObjectHashMap -> Collections.singletonMap(
                           NAME, objectObjectHashMap
                       ));
    }

    /**
     * Returns the cache as a {@link Maybe}.
     *
     * @param name The name of the cache to retrieve
     * @return The cache as a {@link Single}
     */
    @Read
    public Maybe<Map<String, Object>> getCache(@NotBlank @Selector String name) {
        try {
            final Cache<Object> cache = cacheManager.getCache(name);
            return Flowable.fromPublisher(cache.getCacheInfo())
                           .map(CacheInfo::get)
                           .singleElement();
        } catch (ConfigurationException e) {
            // no cache exists
            return Maybe.empty();
        }
    }

    /**
     * Invalidates all the caches.
     *
     * @return A maybe that emits a boolean.
     */
    @Delete
    public Maybe<Boolean> invalidateCaches() {
        return Flowable.fromIterable(cacheManager.getCacheNames())
                .map(cacheManager::getCache)
                .flatMap(c ->
                        Publishers.fromCompletableFuture(() -> c.async().invalidateAll())
                ).reduce((aBoolean, aBoolean2) -> aBoolean && aBoolean2);
    }

    /**
     * Invalidates the cache.
     *
     * @param name The name of the cache to invalidate
     * @return A maybe that emits a boolean if the operation was successful
     */
    @Delete
    public Maybe<Boolean> invalidateCache(@NotBlank @Selector String name) {
        try {
            final SyncCache<Object> cache = cacheManager.getCache(name);
            return Maybe.create(emitter -> cache.async().invalidateAll().whenComplete((aBoolean, throwable) -> {
                if (throwable != null) {
                    emitter.onError(throwable);
                } else {
                    emitter.onSuccess(aBoolean);
                    emitter.onComplete();
                }
            }));
        } catch (ConfigurationException e) {
            // no cache
            return Maybe.empty();
        }
    }

}
