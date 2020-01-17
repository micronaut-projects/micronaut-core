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
package io.micronaut.cache;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.ConversionService;

/**
 * Factory to create {@link DefaultSyncCache} instances based on configuration.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.3.0
 */
@Factory
public class DefaultSyncCacheFactory {

    private final ApplicationContext applicationContext;
    private final ConversionService<?> conversionService;

    /**
     * Creates a default sync cache factory.
     *
     * @param applicationContext the application context
     * @param conversionService the conversion service
     */
    public DefaultSyncCacheFactory(ApplicationContext applicationContext, ConversionService<?> conversionService) {
        this.applicationContext = applicationContext;
        this.conversionService = conversionService;
    }

    /**
     * Factory method to create {@link DefaultSyncCache} instances.
     *
     * @param cacheConfiguration the cache configuration
     * @return a {@link DefaultSyncCache} instance
     */
    @EachBean(CacheConfiguration.class)
    DefaultSyncCache defaultSyncCache(@Parameter CacheConfiguration cacheConfiguration) {
        if (cacheConfiguration.isEnabled()) {
            return new DefaultSyncCache(cacheConfiguration, applicationContext, conversionService);
        } else {
            return null;
        }
    }

}
