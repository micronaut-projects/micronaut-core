package io.micronaut.cache

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class CacheInterceptorSpec extends Specification {
    void "Cacheable completableFuture method called only 1 time"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.caches.slow-cache.maximum-size':10
        )
        CacheableService service = ctx.getBean(CacheableService)
        service.counter.set(0)

        when:
        def firstCompletableFuture = service.slowCompletableFutureCall()
        def secondCompletableFuture = service.slowCompletableFutureCall()
        def startTime = System.currentTimeMillis()
        def firstString = firstCompletableFuture.get()
        def secondString = secondCompletableFuture.get()
        def endTime = System.currentTimeMillis()

        then:
        endTime - startTime < 600
        firstString == secondString
        service.counter.get() == 1
    }

    void "Cacheable publisher method called only 1 time"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.caches.slow-cache.maximum-size':10
        )
        CacheableService service = ctx.getBean(CacheableService)
        service.counter.set(0)

        when:
        def firstSingle = service.slowPublisherCall()
        def secondSingle = service.slowPublisherCall()
        def startTime = System.currentTimeMillis()
        def firstString = firstSingle.blockingGet()
        def secondString = secondSingle.blockingGet()
        def endTime = System.currentTimeMillis()

        then:
        endTime - startTime < 600
        firstString == secondString
        service.counter.get() == 1
    }
}
