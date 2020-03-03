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

/**
 * <p>An interface for handling cache errors.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface CacheErrorHandler {

    /**
     * Handles a cache {@link io.micronaut.cache.annotation.CacheInvalidate} error. Defaults to simply rethrowing the error.
     * By returning <tt>false</tt> cache invalidate errors will instead to be swallowed and ignored.
     *
     * @param cache The cache
     * @param key   The key
     * @param e     The error
     * @return Whether the exception should be swallowed or rethrown. A value of true will rethrow he exception.
     */
    default boolean handleInvalidateError(Cache<?> cache, Object key, RuntimeException e) {
        return true;
    }

    /**
     * Handles a cache {@link io.micronaut.cache.annotation.CacheInvalidate} error. Defaults to simply rethrowing the error.
     * By returning <tt>false</tt> cache invalidate errors will instead to be swallowed and ignored.
     *
     * @param cache The cache
     * @param e     The error
     * @return Whether the exception should be swallowed or rethrown. A value of true will rethrow he exception.
     */
    default boolean handleInvalidateError(Cache<?> cache, RuntimeException e) {
        return true;
    }

    /**
     * Handles a cache {@link io.micronaut.cache.annotation.CachePut} error. Defaults to simply rethrowing the error.
     * By returning <tt>false</tt> cache write errors will instead to be swallowed and ignored.
     *
     * @param cache The cache
     * @param key The key name
     * @param result The result
     * @param e     The error
     * @return Whether the exception should be swallowed or rethrown. A value of true will rethrow he exception.
     */
    default boolean handlePutError(Cache<?> cache, Object key, Object result, RuntimeException e) {
        return true;
    }

    /**
     * Handles an error loading a value from the cache via {@link io.micronaut.cache.annotation.Cacheable}. Note that
     * by returning <tt>false</tt> the behaviour can be customized such that cache related exceptions are ignored and
     * the original method invoked.
     *
     * @param cache The cache
     * @param key   The key
     * @param e     The error
     * @return Whether the exception should be swallowed or rethrown. A value of true will rethrow he exception.
     */
    default boolean handleLoadError(Cache<?> cache, Object key, RuntimeException e) {
        return true;
    }
}
