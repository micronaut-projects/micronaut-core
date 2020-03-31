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

import io.micronaut.cache.annotation.CacheInvalidate
import io.micronaut.cache.annotation.CachePut
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.annotation.SingleResult
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Publisher
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Singleton
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CachePublisherSpec extends Specification {

    @Issue(["https://github.com/micronaut-projects/micronaut-core/issues/1197",
            "https://github.com/micronaut-projects/micronaut-core/issues/1082"])
    void "test cache result of publisher"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.caches.num-cache.maximum-size':10,
                'micronaut.caches.mult-cache.maximum-size':10
        )
        HelloService helloService = ctx.getBean(HelloService)

        when:
        def publisher = Single.fromPublisher(helloService.calculateValue(10))
        def single = helloService.multiplyValue(2)
        def invalidate = helloService.multiplyInvalidate(2)

        then:
        publisher.blockingGet() == "Hello 1: 10"
        publisher.blockingGet() == "Hello 1: 10"
        single.blockingGet() == 4
        invalidate.blockingGet() == 4

        cleanup:
        ctx.close()
    }

    def "test cache atomic - Flowable merging"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.caches.num-cache-atomic-delayed.maximum-size':10
        )
        HelloService helloService = ctx.getBean(HelloService)

        when:
        def merger = Flowable.merge(
                helloService.calculateValueDelayed(10),
                helloService.calculateValueDelayed(10)
        )

        println("Startin at ${LocalTime.now()}")
        then:
        def all = merger.blockingIterable().asList()

        all.size() == 2
        all.every {
            it == "Hello 1: 10"
        }
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

        @Cacheable(value = "num-cache-atomic-delayed", atomic = true)
        @SingleResult
        Publisher<String> calculateValueDelayed(Integer num) {
            return Flowable.fromCallable({ ->
                def n = invocations.incrementAndGet()
                println("Calculating value for $num on ${Thread.currentThread()} at ${LocalTime.now()}")
                return "Hello $n: $num".toString()
            })
            // delay may break the atomicity
            // similarly to issuing an http client call
                    .delay(5, TimeUnit.SECONDS, Schedulers.io())
        }

        @CachePut("mult-cache")
        Single<Integer> multiplyValue(Integer num) {
            Single.<Integer>create({ SingleEmitter emitter ->
                emitter.onSuccess(num * 2)
            })
        }

        @CacheInvalidate("mult-cache")
        Single<Integer> multiplyInvalidate(Integer num) {
            Single.<Integer>create({ SingleEmitter emitter ->
                emitter.onSuccess(num * 2)
            })
        }
    }
}
