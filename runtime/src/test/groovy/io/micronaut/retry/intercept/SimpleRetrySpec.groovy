/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.retry.intercept

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import io.reactivex.Single
import reactor.core.publisher.Mono
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class SimpleRetrySpec extends Specification {

    void "test simple blocking retry"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
        int result = counterService.getCount()

        then:"It executes until successful"
        result == 3
        listener.events.size() == 2

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.count = 0
        counterService.getCount()

        then:"The original exception is thrown"
        def e = thrown(IllegalStateException)
        e.message == "Bad count"

        cleanup:
        context.stop()
    }

    void "test simply retry with rxjava"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
        int result = counterService.getCountSingle().blockingGet()

        then:"It executes until successful"
        listener.events.size() == 2
        result == 3

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.count = 0
        def single = counterService.getCountSingle()
        single.blockingGet()

        then:"The original exception is thrown"
        def e = thrown(IllegalStateException)
        e.message == "Bad count"

        cleanup:
        context.stop()
    }

    void "test simply retry with reactor"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
        int result = counterService.getCountMono().block()

        then:"It executes until successful"
        listener.events.size() == 2
        result == 3

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.count = 0
        def single = counterService.getCountMono()
        single.block()

        then:"The original exception is thrown"
        def e = thrown(IllegalStateException)
        e.message == "Bad count"

        cleanup:
        context.stop()
    }

    @Singleton
    static class MyRetryListener implements RetryEventListener {

        List<RetryEvent> events = []

        void reset() {
            events.clear()
        }
        @Override
        void onApplicationEvent(RetryEvent event) {
            events.add(event)
        }
    }
    @Singleton
    static class CounterService {
        int count = 0
        int countRx = 0
        int countReact = 0
        int countThreshold = 3

        @Retryable(attempts = '5', delay = '5ms')
        int getCount() {
            count++
            if(count < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return count
        }

        @Retryable(attempts = '5', delay = '5ms')
        Single<Integer> getCountSingle() {
            Single.fromCallable({->
                countRx++
                if(countRx < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return countRx
            })
        }

        @Retryable(attempts = '5', delay = '5ms')
        Mono<Integer> getCountMono() {
            Mono.fromCallable({->
                countReact++
                if(countReact < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return countReact
            })
        }
    }
}
