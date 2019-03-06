package io.micronaut.docs.whatsNew

// tag::imports[]
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory

import javax.cache.CacheManager
import javax.cache.Caching
import javax.cache.configuration.MutableConfiguration
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Factory
class CacheFactory {

    @Singleton
    @Bean
    CacheManager cacheManager() {
        CacheManager cacheManager = Caching.cachingProvider.cacheManager
        cacheManager.createCache("my-cache", new MutableConfiguration())
        cacheManager
    }
}
// end::class[]