package io.micronaut.cache

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import spock.lang.Specification

import javax.inject.Singleton

class DynamicCacheSpec extends Specification {

    void "test behavior in the presence of a dynamic cache manager"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'spec.name': DynamicCacheSpec.simpleName,
                'micronaut.caches.counter.initialCapacity':10,
                'micronaut.caches.counter.testMode':true,
                'micronaut.caches.counter.maximumSize':20,
                'micronaut.caches.counter2.initialCapacity':10,
                'micronaut.caches.counter2.maximumSize':20,
                'micronaut.caches.counter2.testMode':true
        )
        CacheManager cacheManager = applicationContext.getBean(CacheManager)

        expect:
        cacheManager.cacheNames == ["counter", "counter2"] as Set

        when:
        SyncCache cache = cacheManager.getCache('counter')

        then:
        noExceptionThrown()
        cache != null

        when:
        cache = cacheManager.getCache("fooBar")

        then:
        noExceptionThrown()
        cache instanceof DynamicCache
        cacheManager.cacheNames == ["counter", "counter2", "fooBar"] as Set

        cleanup:
        applicationContext.close()
    }

    @Requires(property = "spec.name", value = "DynamicCacheSpec")
    @Singleton
    static class MyDynamicCacheManager implements DynamicCacheManager<Map> {

        @Override
        SyncCache<Map> getCache(String name) {
            return new DynamicCache()
        }
    }

}
