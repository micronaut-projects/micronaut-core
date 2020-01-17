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
package io.micronaut.cache

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.async.annotation.SingleResult
import io.reactivex.Flowable
import io.reactivex.Single
import io.micronaut.cache.annotation.CacheConfig
import io.micronaut.cache.annotation.CacheInvalidate
import io.micronaut.cache.annotation.CachePut
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.cache.annotation.InvalidateOperations
import io.micronaut.cache.annotation.PutOperations
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Retry
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Retry
class SyncCacheSpec extends Specification {

    void "test cacheable annotations"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'micronaut.caches.counter.initialCapacity':10,
                'micronaut.caches.counter.testMode':true,
                'micronaut.caches.counter.maximumSize':20,
                'micronaut.caches.counter2.initialCapacity':10,
                'micronaut.caches.counter2.maximumSize':20,
                'micronaut.caches.counter2.testMode':true
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
        counterService.stageValue("test").toCompletableFuture().get() == 1
        counterService.singleValue("test").blockingGet() == 1
        counterService.getValue("test") == 1

        when:
        result = counterService.incrementNoCache("test")

        then:
        result == 2
        counterService.flowableValue("test").blockingFirst() == 1
        counterService.futureValue("test").get() == 1
        counterService.stageValue("test").toCompletableFuture().get() == 1
        counterService.singleValue("test").blockingGet() == 1
        counterService.getValue("test") == 1

        when:
        counterService.reset("test")

        then:
        counterService.getValue("test") == 0

        when:
        counterService.reset("test")

        then:
        counterService.futureValue("test").get() == 0
        counterService.stageValue("test").toCompletableFuture().get() == 0

        when:
        counterService.set("test", 3)

        then:
        counterService.getValue("test") == 3
        counterService.futureValue("test").get() == 3
        counterService.stageValue("test").toCompletableFuture().get() == 3

        when:
        result = counterService.increment("test")

        then:
        result == 4
        counterService.getValue("test") == 4
        counterService.futureValue("test").get() == 4
        counterService.stageValue("test").toCompletableFuture().get() == 4

        when:
        result = counterService.futureIncrement("test").get()

        then:
        result == 5
        counterService.getValue("test") == 5
        counterService.futureValue("test").get() == 5
        counterService.stageValue("test").toCompletableFuture().get() == 5

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

    void "test publisher cache methods are not called for hits"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'micronaut.caches.counter.initialCapacity':10,
                'micronaut.caches.counter.testMode':true,
                'micronaut.caches.counter.maximumSize':20,
        )

        PublisherService publisherService = applicationContext.getBean(PublisherService)

        expect:
        publisherService.callCount.get() == 0

        when:
        publisherService.flowableValue("abc").blockingFirst()

        then:
        publisherService.callCount.get() == 1

        when:
        publisherService.flowableValue("abc").blockingFirst()

        then:
        publisherService.callCount.get() == 1

        when:
        publisherService.singleValue("abcd").blockingGet()

        then:
        publisherService.callCount.get() == 2

        when:
        publisherService.singleValue("abcd").blockingGet()

        then:
        publisherService.callCount.get() == 2
    }

    void "test configure sync cache"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'micronaut.caches.test.initialCapacity':1,
                'micronaut.caches.test.maximumSize':3,
                'micronaut.caches.test.test-mode':true,
        )

        when:
        SyncCache syncCache = applicationContext.getBean(SyncCache, Qualifiers.byName('test'))

        then:
        syncCache.name == 'test'

        when:
        syncCache.put("one", 1)
        syncCache.put("two", 2)
        syncCache.put("three", 3)
        syncCache.put("four", 4)
        syncCache.nativeCache.cleanUp()
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        then:
        conditions.eventually {
            !syncCache.get("one", Integer).isPresent()
            syncCache.get("two", Integer).isPresent()
            syncCache.get("three", Integer).isPresent()
            syncCache.get("four", Integer).isPresent()
        }

        when:
        syncCache.invalidate("two")

        then:
        conditions.eventually {
            !syncCache.get("one", Integer).isPresent()
            !syncCache.get("two", Integer).isPresent()
            syncCache.get("three", Integer).isPresent()
            syncCache.putIfAbsent("three", 3).isPresent()
            syncCache.get("four", Integer).isPresent()
        }


        when:
        syncCache.invalidateAll()

        then:
        conditions.eventually {
            !syncCache.get("one", Integer).isPresent()
            !syncCache.get("two", Integer).isPresent()
            !syncCache.get("three", Integer).isPresent()
            !syncCache.get("four", Integer).isPresent()
        }

        and:
        !syncCache.putIfAbsent("five", 5).isPresent()
        syncCache.putIfAbsent("five", 6).get() == 5

        cleanup:
        applicationContext.stop()
    }

    void "test exception is thrown if non configured cache is retrieved"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'micronaut.caches.test.initialCapacity':1,
                'micronaut.caches.test.maximumSize':3
        )
        CacheManager cacheManager = applicationContext.getBean(CacheManager)

        when:
        cacheManager.getCache("fooBar")

        then:
        def ex = thrown(ConfigurationException)
        ex.message == "No cache configured for name: fooBar"

        cleanup:
        applicationContext.stop()
    }

    @Singleton
    @CacheConfig('counter')
    static class PublisherService {

        AtomicInteger callCount = new AtomicInteger()

        @Cacheable
        @SingleResult
        Flowable<Integer> flowableValue(String name) {
            callCount.incrementAndGet()
            return Flowable.just(0)
        }

        @Cacheable
        Single<Integer> singleValue(String name) {
            callCount.incrementAndGet()
            return Single.just(0)
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
        CompletionStage<Integer> stageValue(String name) {
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
