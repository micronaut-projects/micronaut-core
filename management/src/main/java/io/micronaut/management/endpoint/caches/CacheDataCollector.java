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

import io.micronaut.cache.SyncCache;
import org.reactivestreams.Publisher;

import java.util.List;

/**
 * Used to respond with cache information used for the {@link CachesEndpoint}.
 *
 * @param <T> The type
 * @author Marcel Overdijk
 * @since 1.1
 */
public interface CacheDataCollector<T> {

    /**
     * @param caches A java stream of caches
     * @return A publisher that returns data representing all of the given caches
     */
    Publisher<T> getData(List<SyncCache> caches);

    /**
     * @param cache The cache
     * @return A publisher that returns data representing the given cache
     */
    Publisher<T> getData(SyncCache cache);
}
