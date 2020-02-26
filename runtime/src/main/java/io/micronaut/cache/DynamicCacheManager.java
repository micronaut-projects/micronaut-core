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

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * <p>A contract for a cache manager that does not have pre-defined caches.</p>
 *
 * @param <C> The native cache implementation
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public interface DynamicCacheManager<C> {

    /**
     * Retrieve a cache for the given name. If the cache does not previously exist, a new one will be created.
     * The cache instance should not be cached internally because the cache manager will maintain the instance
     * for future requests.
     *
     * @param name The name of the cache
     * @return The {@link SyncCache} instance
     */
    @NonNull
    SyncCache<C> getCache(String name);
}
