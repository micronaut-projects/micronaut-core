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
package io.micronaut.docs.whatsNew;

// tag::imports[]
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Factory
class CacheFactory {

    @Singleton
    CacheManager cacheManager() {
        CacheManager cacheManager = Caching.getCachingProvider()
                .getCacheManager();
        cacheManager.createCache("my-cache", new MutableConfiguration());
        return cacheManager;
    }
}
// end::class[]