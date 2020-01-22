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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

/**
 * Default {@link DynamicCacheManager} implementation that creates {@link DefaultSyncCache}s with default values.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 1.3.0
 */
@Singleton
@Requires(missingBeans = DynamicCacheManager.class)
@Requires(property = "micronaut.cache.dynamic", notEquals = StringUtils.FALSE)
public class DefaultDynamicCacheManager implements DynamicCacheManager<com.github.benmanes.caffeine.cache.Cache> {

    private final ApplicationContext applicationContext;
    private final ConversionService<?> conversionService;
    private final ApplicationConfiguration applicationConfiguration;

    /**
     * Creates a default dynamic cache manager.
     *  @param applicationContext the application context
     * @param conversionService the conversion service
     * @param applicationConfiguration the application configuration
     */
    public DefaultDynamicCacheManager(ApplicationContext applicationContext, ConversionService<?> conversionService, ApplicationConfiguration applicationConfiguration) {
        this.applicationContext = applicationContext;
        this.conversionService = conversionService;
        this.applicationConfiguration = applicationConfiguration;
    }

    @Nonnull
    @Override
    public SyncCache<com.github.benmanes.caffeine.cache.Cache> getCache(String name) {
        return new DefaultSyncCache(new DefaultCacheConfiguration(name, applicationConfiguration), applicationContext, conversionService);
    }
}
