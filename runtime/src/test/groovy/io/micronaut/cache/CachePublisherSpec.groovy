package io.micronaut.cache

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.annotation.SingleResult
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Publisher
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

class CachePublisherSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1082')
    void "test cache result of publisher"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.caches.num-cache.maximum-size':10
        )
        HelloService helloService = ctx.getBean(HelloService)


        when:
        def publisher = Single.fromPublisher(helloService.calculateValue(10))

        then:
        publisher.blockingGet() == "Hello 1: 10"
        publisher.blockingGet() == "Hello 1: 10"


        cleanup:
        ctx.close()
    }


    @Singleton
    static class HelloService {

        AtomicInteger invocations = new AtomicInteger(0)
        @Cacheable("num-cache")
        @SingleResult
        Publisher<String> calculateValue(Integer num) {
            return Flowable.fromCallable({->
                def n = invocations.incrementAndGet()
                println("Calculating value for $num")
                return "Hello $n: $num".toString()
            })
        }
    }
}
