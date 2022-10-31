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
package io.micronaut.retry.intercept

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.event.CircuitClosedEvent
import io.micronaut.retry.event.CircuitOpenEvent
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import io.micronaut.core.async.annotation.SingleResult

/**
 * @author graemerocher
 * @since 1.0
 */
class CircuitBreakerSpec extends Specification{


    void "test blocking circuit breaker"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)
        MyCircuitOpenEventListener circuitOpenEventListener = context.getBean(MyCircuitOpenEventListener)
        MyCircuitClosedEventListener circuitClosedEventListener = context.getBean(MyCircuitClosedEventListener)

        when:"A method is annotated retry"
        int result = counterService.getCount()

        then:"It executes until successful"
        result == 3
        listener.events.size() == 2

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.countValue = 0
        counterService.getCount()

        then:"The original exception is thrown"
        def e = thrown(IllegalStateException)
        e.message == "Bad count"
        counterService.countValue == 6
        listener.events.size() == 5
        circuitOpenEventListener.lastEvent
        circuitOpenEventListener.lastEvent.source.methodName == 'getCount'

        when:"We attempt to execute the method again"
        circuitOpenEventListener.lastEvent = null
        PollingConditions pollingConditions = new PollingConditions()
        counterService.getCount()

        then:"The exception is rethrown but the original logic is never invoked"
        e = thrown(IllegalStateException)
        e.message == "Bad count"
        listener.events.size() == 5
        counterService.countValue == 6
        circuitOpenEventListener.lastEvent == null
        circuitClosedEventListener.lastEvent == null

        when:"The service is reset to a valid state"
        listener.reset()
        counterService.countThreshold = 3
        counterService.countValue=0
        println "counterService.countThreshold = $counterService.countThreshold"
        counterService.getCount()

        then:"The exception continues to thrown until the timeout is reached"
        e = thrown(IllegalStateException)
        e.message == "Bad count"

        and:
        circuitOpenEventListener.lastEvent == null
        circuitClosedEventListener.lastEvent == null
        listener.events.size() == 0

        pollingConditions.eventually {
            counterService.getCount() == 3
            circuitClosedEventListener.lastEvent != null
        }

        and:"Only 1 event was fired since the circuit was half open"
        listener.events.size() == 1

        cleanup:
        context.stop()
    }


    void "test rxjava circuit breaker"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)
        MyCircuitOpenEventListener circuitOpenEventListener = context.getBean(MyCircuitOpenEventListener)
        MyCircuitClosedEventListener circuitClosedEventListener = context.getBean(MyCircuitClosedEventListener)

        when:"A method is annotated retry"
        int result = Mono.from(counterService.getCountSingle()).block()

        then:"It executes until successful"
        result == 3
        listener.events.size() == 2

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.countRx = 0
        Mono.from(counterService.getCountSingle()).block()

        then:"The original exception is thrown"
        IllegalStateException e = thrown()
        e.message == "Bad count"
        counterService.countRx == 6
        listener.events.size() == 5
        circuitOpenEventListener.lastEvent
        circuitOpenEventListener.lastEvent.source.methodName == 'getCountSingle'

        when:"We attempt to execute the method again"
        circuitOpenEventListener.lastEvent = null
        PollingConditions pollingConditions = new PollingConditions()
        Mono.from(counterService.getCountSingle()).block()

        then:"The exception is rethrown but the original logic is never invoked"
        e = thrown(IllegalStateException)
        e.message == "Bad count"
        listener.events.size() == 5
        counterService.countRx == 6
        circuitOpenEventListener.lastEvent == null
        circuitClosedEventListener.lastEvent == null

        when:"The service is reset to a valid state"
        listener.reset()
        counterService.countThreshold = 3
        counterService.countRx=0
        Mono.from(counterService.getCountSingle()).block()

        then:"The exception continues to thrown until the timeout is reached"
        e = thrown(IllegalStateException)
        e.message == "Bad count"

        and:
        circuitOpenEventListener.lastEvent == null
        circuitClosedEventListener.lastEvent == null
        listener.events.size() == 0

        pollingConditions.eventually {
            Mono.from(counterService.getCountSingle()).block() == 3
            circuitClosedEventListener.lastEvent != null
        }

        and:"Only 1 event was fired since the circuit was half open"
        listener.events.size() == 1

        cleanup:
        context.stop()
    }

    void "test circuit breaker empty config"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        EmptyConfigService service = context.getBean(EmptyConfigService)

        when:"A method is annotated retry"
        int result = service.getCount()

        then:"It executes until successful"
        result == 2
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
    static class MyCircuitOpenEventListener implements ApplicationEventListener<CircuitOpenEvent> {
        CircuitOpenEvent lastEvent
        @Override
        void onApplicationEvent(CircuitOpenEvent event) {
            lastEvent = event
        }
    }


    @Singleton
    static class MyCircuitClosedEventListener implements ApplicationEventListener<CircuitClosedEvent> {
        CircuitClosedEvent lastEvent
        @Override
        void onApplicationEvent(CircuitClosedEvent event) {
            lastEvent = event
        }
    }

    @Singleton
    @CircuitBreaker(attempts = '5', delay = '5ms', reset = '300ms')
    static class CounterService {
        int countValue = 0
        int countRx = 0
        int countThreshold = 3


        int getCount() {
            countValue++
            if(countValue < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return countValue
        }

        @SingleResult
        Publisher<Integer> getCountSingle() {
            Mono.fromCallable({->
                countRx++
                println "countValue = $countRx"
                println "countThreshold = $countThreshold"

                if(countRx < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return countRx
            })
        }

    }

    @Singleton
    @CircuitBreaker
    static class EmptyConfigService {
        int countValue = 0
        int countRx = 0
        int countThreshold = 2

        int getCount() {
            countValue++
            if(countValue < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return countValue
        }
    }
}
