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

import io.micronaut.cache.interceptor.CacheInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Async error handler that simply logs errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Named("async")
public class AsyncCacheErrorHandler implements CacheErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CacheInterceptor.class);

    @Override
    public boolean handleInvalidateError(Cache<?> cache, Object key, RuntimeException e) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error invalidating cache [" + cache.getName() + "] for key: " + key, e);
        }
        return false;
    }

    @Override
    public boolean handleInvalidateError(Cache<?> cache, RuntimeException e) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error invalidating cache: " + cache.getName(), e);
        }
        return false;
    }

    @Override
    public boolean handlePutError(Cache<?> cache, Object key, Object result, RuntimeException e) {
        if (LOG.isErrorEnabled()) {
            LOG.error("Error caching value [" + result + "] for key [" + key + "] in cache: " + cache.getName(), e);
        }
        return false;
    }
}
