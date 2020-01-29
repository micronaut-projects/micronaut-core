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
import io.micronaut.retry.CircuitState
import io.micronaut.retry.annotation.CircuitBreaker
import io.reactivex.Single
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton
import java.time.Duration

/**
 * @author graemerocher
 * @since 1.0
 */
class CircuitBreakerRetrySpec extends Specification {

    void "test circuit breaker retry"() {
        when:"A retry is constructed"
        CircuitBreakerRetry retry = new CircuitBreakerRetry(
                1000,
                {->
                    new SimpleRetry(3, 2.0d, Duration.ofMillis(500))
                }, null,null
        )
        retry.open()


        then:
        retry.currentState() == CircuitState.CLOSED

        when:
        retry.close(null)

        then:
        retry.currentState() == CircuitState.CLOSED


        when:
        retry.open()


        then:
        retry.canRetry(new RuntimeException("bad"))
        retry.canRetry(new RuntimeException("bad"))
        retry.canRetry(new RuntimeException("bad"))
        !retry.canRetry(new RuntimeException("bad"))

        when:
        retry.close(new RuntimeException("bad"))

        then:
        retry.currentState() == CircuitState.OPEN


        when:
        PollingConditions conditions = new PollingConditions(timeout: 3)
        retry.open()

        then:
        def e = thrown(RuntimeException)
        e.message == "bad"
        conditions.eventually {
            retry.currentState() == CircuitState.HALF_OPEN
        }

        when:
        retry.open()
        retry.close(new RuntimeException("another bad"))

        then:
        retry.currentState() == CircuitState.OPEN


        when:
        retry.open()

        then:
        retry.currentState() == CircuitState.OPEN
        e = thrown(RuntimeException)
        e.message == "another bad"
        conditions.eventually {
            retry.currentState() == CircuitState.HALF_OPEN
        }

        when:
        retry.open()
        retry.close(null)

        then:
        retry.currentState() == CircuitState.CLOSED
        retry.canRetry(new RuntimeException("bad"))

    }

    @Unroll
    void "test circuit breaker inclusive exception filtering with #name"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        callGetCount(counterService, mode)

        then: "the circuit is open so the original exception is thrown"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        when:
        counterService.countIncludes = 0
        callGetCount(counterService, CountMode.THROW_ILLEGAL_STATE)

        then: "retry didn't kick in because the exception thrown doesn't match includes"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        when:
        callGetCount(counterService, mode)

        then: "the circuit is open so the original exception is thrown"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        cleanup:
        context.stop()

        where:
        name                                                | mode                  | callGetCount
        "includes"                                          | CountMode.DEFAULT     | { service, mode -> service.getCountIncludes(mode) }
        "includesAllOf"                                     | CountMode.THROW_CHILD | { service, mode -> service.getCountIncludesAllOf(mode) }
        "includes and includesAllOf using simple exception" | CountMode.DEFAULT     | { service, mode -> service.getCountIncludesWithIncludesAllOf(mode) }
        "includes and includesAllOf using child exception"  | CountMode.THROW_CHILD | { service, mode -> service.getCountIncludesWithIncludesAllOf(mode) }
    }

    @Unroll
    void "test circuit breaker exclusive exception filtering with #name"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        callGetCount(counterService, CountMode.THROW_ILLEGAL_STATE)

        then: "retry kicks in because the exception thrown doesn't match excludes"
        noExceptionThrown()
        counterService.countExcludes == counterService.countThreshold

        when:
        counterService.countExcludes = 0
        callGetCount(counterService, mode)

        then: "retry didn't kick in because the exception thrown matches excludes"
        thrown(expectedException)
        counterService.countExcludes == 1

        when:
        callGetCount(counterService, CountMode.THROW_ILLEGAL_STATE)

        then: "the circuit is open so the original exception is thrown"
        thrown(expectedException)
        counterService.countExcludes == 1

        cleanup:
        context.stop()

        where:
        name                                                | mode                  | expectedException      | callGetCount
        "excludes"                                          | CountMode.DEFAULT     | MyCustomException      | { service, mode -> service.getCountExcludes(mode) }
        "excludesAllOf"                                     | CountMode.THROW_CHILD | MyCustomChildException | { service, mode -> service.getCountExcludesAllOf(mode) }
        "excludes and excludesAllOf using simple exception" | CountMode.DEFAULT     | MyCustomException      | { service, mode -> service.getCountExcludesWithExcludesAllOf(mode) }
        "excludes and excludesAllOf using child exception"  | CountMode.THROW_CHILD | MyCustomChildException | { service, mode -> service.getCountExcludesWithExcludesAllOf(mode) }
    }

    void "test circuit breaker with a single"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCount().onErrorReturnItem(1).blockingGet() //to trigger the state
        counterService.getCount()

        then:
        noExceptionThrown()

        cleanup:
        context.stop()
    }

    static enum CountMode {
        THROW_ILLEGAL_STATE,
        THROW_CHILD,
        DEFAULT
    }

    @Singleton
    static class CounterService {
        int countIncludes = 0
        int countExcludes = 0
        int countThreshold = 3

        @CircuitBreaker(attempts = '5', delay = '5ms', includes = MyCustomException.class)
        Integer getCountIncludes(CountMode mode) {
            countIncludes++
            if(countIncludes < countThreshold) {
                if (mode == CountMode.THROW_ILLEGAL_STATE) {
                    throw new IllegalStateException("Bad count")
                } else {
                    throw new MyCustomException()
                }
            }
            return countIncludes
        }

        @CircuitBreaker(attempts = '5', delay = '5ms', includesAllOf = MyCustomBaseException.class)
        Integer getCountIncludesAllOf(CountMode mode) {
            countIncludes++
            if(countIncludes < countThreshold) {
                if (mode == CountMode.THROW_ILLEGAL_STATE) {
                    throw new IllegalStateException("Bad count")
                } else {
                    throw new MyCustomChildException()
                }
            }
            return countIncludes
        }

        @CircuitBreaker(attempts = '5', delay = '5ms', includes = MyCustomException.class, includesAllOf = MyCustomBaseException.class)
        Integer getCountIncludesWithIncludesAllOf(CountMode mode) {
            countIncludes++
            if(countIncludes < countThreshold) {
                if (mode == CountMode.THROW_ILLEGAL_STATE) {
                    throw new IllegalStateException("Bad count")
                } else if (mode == CountMode.THROW_CHILD) {
                    throw new MyCustomChildException()
                } else {
                    throw new MyCustomException()
                }
            }
            return countIncludes
        }

        @CircuitBreaker(attempts = '5', delay = '5ms', excludes = MyCustomException.class)
        Integer getCountExcludes(CountMode mode) {
            countExcludes++
            if(countExcludes < countThreshold) {
                if (mode == CountMode.THROW_ILLEGAL_STATE) {
                    throw new IllegalStateException("Bad count")
                } else {
                    throw new MyCustomException()
                }
            }
            return countExcludes
        }

        @CircuitBreaker(attempts = '5', delay = '5ms', excludesAllOf = MyCustomBaseException.class)
        Integer getCountExcludesAllOf(CountMode mode) {
            countExcludes++
            if(countExcludes < countThreshold) {
                if (mode == CountMode.THROW_ILLEGAL_STATE) {
                    throw new IllegalStateException("Bad count")
                } else {
                    throw new MyCustomChildException()
                }
            }
            return countExcludes
        }

        @CircuitBreaker(attempts = '5', delay = '5ms', excludes = MyCustomException.class, excludesAllOf = MyCustomBaseException.class)
        Integer getCountExcludesWithExcludesAllOf(CountMode mode) {
            countExcludes++
            if(countExcludes < countThreshold) {
                if (mode == CountMode.THROW_ILLEGAL_STATE) {
                    throw new IllegalStateException("Bad count")
                } else if (mode == CountMode.THROW_CHILD) {
                    throw new MyCustomChildException()
                } else {
                    throw new MyCustomException()
                }
            }
            return countExcludes
        }

        @CircuitBreaker(attempts = '1', delay = '0ms')
        Single<Integer> getCount() {
            Single.error(new IllegalStateException("Bad count"))
        }
    }

}
