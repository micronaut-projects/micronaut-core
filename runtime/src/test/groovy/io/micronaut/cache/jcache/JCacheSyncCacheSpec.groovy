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
package io.micronaut.cache.jcache

import io.micronaut.cache.annotation.CacheConfig
import io.micronaut.cache.annotation.CacheInvalidate
import io.micronaut.cache.annotation.CachePut
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.cache.annotation.InvalidateOperations
import io.micronaut.cache.annotation.PutOperations
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.Specification

import javax.cache.CacheManager
import javax.cache.Caching
import javax.cache.configuration.MutableConfiguration
import javax.inject.Singleton
import java.util.concurrent.CompletableFuture

class JCacheSyncCacheSpec extends Specification {
    void "test cacheable annotations with jcache"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                (JCacheManager.JCACHE_ENABLED):true
        )

        when:
        CounterService counterService = applicationContext.getBean(CounterService)

        then:
        counterService.flowableValue("test").blockingFirst() == 0
        counterService.singleValue("test").blockingGet() == 0

        when:
        counterService.reset()
        def result =counterService.increment("test")

        then:
        result == 1
        counterService.flowableValue("test").blockingFirst() == 1
        counterService.futureValue("test").get() == 1
        counterService.singleValue("test").blockingGet() == 1
        counterService.getValue("test") == 1
        counterService.getValue("test") == 1

        when:
        result = counterService.incrementNoCache("test")

        then:
        result == 2
        counterService.flowableValue("test").blockingFirst() == 1
        counterService.futureValue("test").get() == 1
        counterService.getValue("test") == 1

        when:
        counterService.reset("test")
        then:
        counterService.getValue("test") == 0

        when:
        counterService.reset("test")
        then:
        counterService.futureValue("test").get() == 0



        when:
        counterService.set("test", 3)

        then:
        counterService.getValue("test") == 3
        counterService.futureValue("test").get() == 3

        when:
        result = counterService.increment("test")

        then:
        result == 4
        counterService.getValue("test") == 4
        counterService.futureValue("test").get() == 4

        when:
        result = counterService.futureIncrement("test").get()

        then:
        result == 5
        counterService.getValue("test") == 5
        counterService.futureValue("test").get() == 5

        when:
        counterService.reset()

        then:
        !counterService.getOptionalValue("test").isPresent()
        counterService.getValue("test") == 0
        counterService.getOptionalValue("test").isPresent()
        counterService.getValue2("test") == 0

        when:
        counterService.increment("test")
        counterService.increment("test")

        then:
        counterService.getValue("test") == 2
        counterService.getValue2("test") == 0

        when:
        counterService.increment2("test")

        then:
        counterService.getValue("test") == 1
        counterService.getValue2("test") == 1


    }

    @Factory
    @Requires(property = JCacheManager.JCACHE_ENABLED, value = "true")
    static class CacheFactory {

        @Singleton
        @Requires(property = JCacheManager.JCACHE_ENABLED, value = "true")
        CacheManager cacheManager() {
            def cacheManager = Caching.getCachingProvider().cacheManager
            cacheManager.createCache('counter', new MutableConfiguration())
            cacheManager.createCache('counter2', new MutableConfiguration())
            return cacheManager
        }
    }

    @Singleton
    @CacheConfig('counter')
    static class CounterService {
        Map<String, Integer> counters = new LinkedHashMap<>()
        Map<String, Integer> counters2 = new LinkedHashMap<>()

        int incrementNoCache(String name) {
            int value = counters.computeIfAbsent(name, { 0 })
            counters.put(name, ++value)
            return value
        }

        @CachePut
        int increment(String name) {
            int value = counters.computeIfAbsent(name, { 0 })
            counters.put(name, ++value)
            return value
        }

        @PutOperations([
                @CachePut('counter'),
                @CachePut('counter2')

        ])
        int increment2(String name) {
            int value = counters2.computeIfAbsent(name, { 0 })
            counters2.put(name, ++value)
            return value
        }

        @Cacheable
        CompletableFuture<Integer> futureValue(String name) {
            return CompletableFuture.completedFuture(counters.computeIfAbsent(name, { 0 }))
        }

        @Cacheable
        @SingleResult
        Flowable<Integer> flowableValue(String name) {
            return Flowable.just(counters.computeIfAbsent(name, { 0 }))
        }

        @Cacheable
        Single<Integer> singleValue(String name) {
            return Single.just(counters.computeIfAbsent(name, { 0 }))
        }

        @CachePut
        CompletableFuture<Integer> futureIncrement(String name) {
            int value = counters.computeIfAbsent(name, { 0 })
            counters.put(name, ++value)
            return CompletableFuture.completedFuture(value)
        }

        @Cacheable
        int getValue(String name) {
            return counters.computeIfAbsent(name, { 0 })
        }

        @Cacheable('counter2')
        int getValue2(String name) {
            return counters2.computeIfAbsent(name, { 0 })
        }

        @Cacheable
        Optional<Integer> getOptionalValue(String name) {
            return Optional.ofNullable(counters.get(name))
        }

        @CacheInvalidate(all = true)
        void reset() {
            counters.clear()
        }

        @CacheInvalidate
        void reset(String name) {
            counters.remove(name)
        }

        @InvalidateOperations([
                @CacheInvalidate('counter'),
                @CacheInvalidate('counter2')
        ])
        void reset2(String name) {
            counters.remove(name)
        }

        @CacheInvalidate(parameters = 'name')
        void set(String name, int val) {
            counters.put(name, val)
        }
    }
}
