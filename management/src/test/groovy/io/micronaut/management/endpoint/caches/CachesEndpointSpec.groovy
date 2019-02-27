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
package io.micronaut.management.endpoint.caches

import io.micronaut.cache.CacheManager
import io.micronaut.cache.SyncCache
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * @author Marcel Overdijk
 * @since 1.1
 */
class CachesEndpointSpec extends Specification {

    void "test no caches are returned from caches endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                "endpoints.caches.enabled": true,
                "endpoints.caches.sensitive": false
        ], Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange("/caches", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result == [:]

        cleanup:
        rxClient.close()
        embeddedServer?.close()
    }

    void "test caches are returned from caches endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                [
                        "endpoints.caches.enabled": true,
                        "endpoints.caches.sensitive": false,
                        "micronaut.caches.foo-cache.maximumSize": 10,
                        "micronaut.caches.foo-cache.recordStats": true,
                        "micronaut.caches.bar-cache.maximumWeight": 20
                ],Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        CacheManager cacheManager = embeddedServer.applicationContext.getBean(CacheManager)

        SyncCache fooCache = cacheManager.getCache("foo-cache")
        fooCache.put("foo1", "value1")
        fooCache.put("foo2", "value2")
        fooCache.get("foo1", Object)
        fooCache.get("foo2", Object)
        fooCache.get("foo2", Object)
        fooCache.get("foo3", Object)

        when:
        def response = rxClient.exchange("/caches", Map).blockingFirst()
        Map result = response.body()
        Map<String, Map<String, Object>> caches = result.caches

        then:
        response.code() == HttpStatus.OK.code
        caches.size() == 2
        caches["bar-cache"].implementationClass == "com.github.benmanes.caffeine.cache.BoundedLocalCache\$BoundedLocalManualCache"
        caches["bar-cache"].caffeine.estimatedSize == 0
        caches["bar-cache"].caffeine.maximumWeight == 20
        caches["bar-cache"].caffeine.weightedSize == 0
        caches["bar-cache"].caffeine.recordingStats == false
        caches["bar-cache"].caffeine.stats == null
        caches["foo-cache"].caffeine.estimatedSize == 2
        caches["foo-cache"].caffeine.maximumSize == 10
        caches["foo-cache"].caffeine.recordingStats == true
        caches["foo-cache"].caffeine.stats.requestCount == 4
        caches["foo-cache"].caffeine.stats.hitCount == 3
        caches["foo-cache"].caffeine.stats.hitRate == 0.75
        caches["foo-cache"].caffeine.stats.missCount == 1
        caches["foo-cache"].caffeine.stats.missRate == 0.25
        caches["foo-cache"].caffeine.stats.evictionCount == 0
        caches["foo-cache"].caffeine.stats.evictionWeight == 0

        when:
        response = rxClient.exchange("/caches/foo-cache", Map).blockingFirst()
        result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        caches.size() == 2
        result.implementationClass == "com.github.benmanes.caffeine.cache.BoundedLocalCache\$BoundedLocalManualCache"
        result.caffeine.estimatedSize == 2
        result.caffeine.maximumSize == 10
        result.caffeine.recordingStats == true
        result.caffeine.stats.requestCount == 4
        result.caffeine.stats.hitCount == 3
        result.caffeine.stats.hitRate == 0.75
        result.caffeine.stats.missCount == 1
        result.caffeine.stats.missRate == 0.25
        result.caffeine.stats.evictionCount == 0
        result.caffeine.stats.evictionWeight == 0

        cleanup:
        rxClient.close()
        embeddedServer?.close()
    }

    void "test invalidate single cache"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                [
                        "endpoints.caches.enabled": true,
                        "endpoints.caches.sensitive": false,
                        "micronaut.caches.foo-cache.maximumSize": 10,
                        "micronaut.caches.bar-cache.maximumSize": 10
                ],Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        CacheManager cacheManager = embeddedServer.applicationContext.getBean(CacheManager)

        SyncCache fooCache = cacheManager.getCache("foo-cache")
        fooCache.put("foo1", "value1")
        fooCache.put("foo2", "value2")

        SyncCache barCache = cacheManager.getCache("bar-cache")
        barCache.put("bar1", "value1")
        barCache.put("bar2", "value2")

        when:
        def response = rxClient.exchange("/caches", Map).blockingFirst()
        Map result = response.body()
        Map<String, Map<String, Object>> caches = result.caches

        then:
        response.code() == HttpStatus.OK.code
        caches.size() == 2
        caches["bar-cache"].caffeine.estimatedSize == 2
        caches["foo-cache"].caffeine.estimatedSize == 2

        when:
        rxClient.exchange(HttpRequest.DELETE("/caches/foo-cache")).blockingFirst()
        response = rxClient.exchange("/caches", Map).blockingFirst()
        result = response.body()
        caches = result.caches

        then:
        response.code() == HttpStatus.OK.code
        caches.size() == 2
        caches["bar-cache"].caffeine.estimatedSize == 2
        caches["foo-cache"].caffeine.estimatedSize == 0

        cleanup:
        rxClient.close()
        embeddedServer?.close()
    }

    void "test invalidate all caches"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                [
                        "endpoints.caches.enabled": true,
                        "endpoints.caches.sensitive": false,
                        "micronaut.caches.foo-cache.maximumSize": 10,
                        "micronaut.caches.bar-cache.maximumSize": 10
                ],Environment.TEST)
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
        CacheManager cacheManager = embeddedServer.applicationContext.getBean(CacheManager)

        SyncCache fooCache = cacheManager.getCache("foo-cache")
        fooCache.put("foo1", "value1")
        fooCache.put("foo2", "value2")

        SyncCache barCache = cacheManager.getCache("bar-cache")
        barCache.put("bar1", "value1")
        barCache.put("bar2", "value2")

        when:
        def response = rxClient.exchange("/caches", Map).blockingFirst()
        Map result = response.body()
        Map<String, Map<String, Object>> caches = result.caches

        then:
        response.code() == HttpStatus.OK.code
        caches.size() == 2
        caches["bar-cache"].caffeine.estimatedSize == 2
        caches["foo-cache"].caffeine.estimatedSize == 2

        when:
        rxClient.exchange(HttpRequest.DELETE("/caches")).blockingFirst()
        response = rxClient.exchange("/caches", Map).blockingFirst()
        result = response.body()
        caches = result.caches

        then:
        response.code() == HttpStatus.OK.code
        caches.size() == 2
        caches["bar-cache"].caffeine.estimatedSize == 0
        caches["foo-cache"].caffeine.estimatedSize == 0

        cleanup:
        rxClient.close()
        embeddedServer?.close()
    }
}
