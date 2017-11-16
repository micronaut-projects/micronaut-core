/*
 * Copyright 2017 original authors
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
package org.particleframework.cache

import org.particleframework.cache.annotation.CacheConfig
import org.particleframework.cache.annotation.CacheEvict
import org.particleframework.cache.annotation.CachePut
import org.particleframework.cache.annotation.Cacheable
import org.particleframework.context.ApplicationContext
import org.particleframework.inject.qualifiers.Qualifiers
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SyncCacheSpec extends Specification {

    void "test cacheable annotations"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'particle.caches.counter.initialCapacity':10,
                'particle.caches.counter.maximumSize':20
        )

        when:
        CounterService counterService = applicationContext.getBean(CounterService)
        def result =counterService.increment("test")

        then:
        result == 1
        counterService.getValue("test") == 1
        counterService.getValue("test") == 1

        when:
        result = counterService.incrementNoCache("test")

        then:
        result == 2
        counterService.getValue("test") == 1

        when:
        counterService.reset("test")
        then:
        counterService.getValue("test") == 0

        when:
        result = counterService.increment("test")

        then:
        result == 1
        counterService.getValue("test") == 1

    }

    void "test configure sync cache"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'particle.caches.test.initialCapacity':1,
                'particle.caches.test.maximumSize':3
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
        sleep(1000)

        then:
        !syncCache.get("one", Integer).isPresent()
        syncCache.get("two", Integer).isPresent()
        syncCache.get("three", Integer).isPresent()
        syncCache.get("four", Integer).isPresent()

        when:
        syncCache.invalidate("two")

        then:
        !syncCache.get("one", Integer).isPresent()
        !syncCache.get("two", Integer).isPresent()
        syncCache.get("three", Integer).isPresent()
        syncCache.putIfAbsent("three", 3).isPresent()
        syncCache.get("four", Integer).isPresent()


        when:
        syncCache.invalidateAll()

        then:
        !syncCache.get("one", Integer).isPresent()
        !syncCache.get("two", Integer).isPresent()
        !syncCache.get("three", Integer).isPresent()
        !syncCache.get("four", Integer).isPresent()

        cleanup:
        applicationContext.stop()
    }

    @Singleton
    @CacheConfig(cacheNames = ['counter'])
    static class CounterService {
        Map<String, Integer> counters = new LinkedHashMap<>()

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

        @Cacheable
        int getValue(String name) {
            return counters.computeIfAbsent(name, { 0 })
        }


        @CacheEvict(all = true)
        void reset() {
            counters.clear()
        }

        @CacheEvict()
        void reset(String name) {
            counters.remove(name)
        }
        @CacheEvict
        void set(String name, int val) {
            counters.put(name, val)
        }
    }
}
