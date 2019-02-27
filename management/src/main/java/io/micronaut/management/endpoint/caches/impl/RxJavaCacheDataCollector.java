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

import io.micronaut.cache.SyncCache;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.caches.CacheData;
import io.micronaut.management.endpoint.caches.CacheDataCollector;
import io.micronaut.management.endpoint.caches.CachesEndpoint;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * A RxJava cache data collector.
 *
 * @author Marcel Overdijk
 * @since 1.1.0
 */
@Singleton
@Requires(beans = CachesEndpoint.class)
public class RxJavaCacheDataCollector implements CacheDataCollector<Map<String, Object>> {

    private final CacheData cacheData;
    private final ExecutorService executorService;

    /**
     * @param cacheData       The cache data provider
     * @param executorService The executor service to run on
     */
    public RxJavaCacheDataCollector(CacheData cacheData,
                                    @Named(TaskExecutors.IO) ExecutorService executorService) {
        this.cacheData = cacheData;
        this.executorService = executorService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Publisher<Map<String, Object>> getData(Publisher<SyncCache> caches) {
        return getCaches(caches)
                .map((cacheMap) -> {
                    Map<String, Object> cacheData = new LinkedHashMap<>(1);
                    cacheData.put("caches", cacheMap);
                    return cacheData;
                }).toFlowable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Publisher getData(final SyncCache cache) {
        return Flowable.just(cacheData.getData(cache));
    }

    /**
     * @param caches The caches
     * @return A {@link Single} that wraps a Map
     */
    protected Single<Map<String, Object>> getCaches(Publisher<SyncCache> caches) {
        Map<String, Object> cacheMap = new LinkedHashMap<>();

        return Flowable
                .fromPublisher(caches)
                .subscribeOn(Schedulers.from(executorService))
                .collectInto(cacheMap, (map, cache) ->
                        map.put(cache.getName(), cacheData.getData(cache))
                );
    }
}
