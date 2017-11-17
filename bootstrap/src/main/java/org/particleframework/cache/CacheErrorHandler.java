/*
 * Copyright 2017 original authors
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
package org.particleframework.cache;

/**
 * <p>An interface for handling cache errors</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface CacheErrorHandler {


    /**
     * Handles a cache {@link org.particleframework.cache.annotation.CacheInvalidate} error. Defaults to simplify throwing it
     *
     * @param cache The cache
     * @param key The key
     * @param e The error
     */
    default void handleInvalidateError(SyncCache cache, Object key, RuntimeException e) {
        throw e;
    }

    /**
     * Handles a cache {@link org.particleframework.cache.annotation.CacheInvalidate} error. Defaults to simplify throwing it
     *
     * @param cache The cache
     * @param e The error
     */
    default void handleInvalidateError(SyncCache cache, RuntimeException e) {
        throw e;
    }

    /**
     * Handles a cache {@link org.particleframework.cache.annotation.CachePut} error. Defaults to simplify throwing it
     *
     * @param cache The cache
     * @param e The error
     */
    default void handlePutError(SyncCache cache, Object key, Object result, RuntimeException e) {
        throw e;
    }

    /**
     * Handles a load error caused by the cache when invoking the cache value {@link java.util.function.Supplier}
     *
     * @param cache The cache
     * @param key The key
     * @param e The error
     * @return The error that will be rethrown to the client
     */
    default RuntimeException handleLoadError(SyncCache cache, Object key, RuntimeException e) {
        return e;
    }
}
