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
    fun cacheManager(): CacheManager {
        val cacheManager = Caching.getCachingProvider().cacheManager
        cacheManager.createCache("my-cache", MutableConfiguration<Any, Any>())
        return cacheManager
    }
}
// end::class[]