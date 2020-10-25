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
import io.micronaut.retry.annotation.RetryPredicate
import io.reactivex.Single
import spock.lang.Specification
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

        when: "calling close with null tests a previous execution that succeeded closing an already open circuit"
        retry.close(null)

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

    void "test circuit breaker with includes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountIncludes(false)

        then: "the circuit is open so the original exception is thrown"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        when:
        counterService.countIncludes = 0
        counterService.getCountIncludes(true)

        then: "retry didn't kick in because the exception thrown doesn't match includes"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        when:
        counterService.getCountIncludes(false)

        then: "the circuit is open so the original exception is thrown"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        cleanup:
        context.stop()
    }

    void "test circuit breaker with excludes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountExcludes(true)

        then: "retry kicks in because the exception thrown doesn't match excludes"
        noExceptionThrown()
        counterService.countExcludes == counterService.countThreshold

        when:
        counterService.countExcludes = 0
        counterService.getCountExcludes(false)

        then: "retry didn't kick in because the exception thrown matches excludes"
        thrown(MyCustomException)
        counterService.countExcludes == 1

        when:
        counterService.getCountExcludes(true)

        then: "the circuit is open so the original exception is thrown"
        thrown(MyCustomException)
        counterService.countExcludes == 1

        cleanup:
        context.stop()
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

    void "test circuit breaker with predicate"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountPredicate(false)

        then: "the circuit is open so the original exception is thrown"
        noExceptionThrown()
        counterService.countPredicate == counterService.countThreshold

        when:
        counterService.countPredicate = 0
        counterService.getCountPredicate(true)

        then: "retry didn't kick in because the exception thrown doesn't match predicate"
        thrown(IllegalStateException)
        counterService.countPredicate == 1

        when:
        counterService.getCountPredicate(false)

        then: "the circuit is open so the original exception is thrown"
        thrown(IllegalStateException)
        counterService.countPredicate == 1

        cleanup:
        context.stop()
    }

    static class MyRetryPredicate implements RetryPredicate {
        @Override
        boolean test(Throwable throwable) {
            return throwable instanceof MyCustomException
        }
    }

    @Singleton
    static class CounterService {
        int countIncludes = 0
        int countExcludes = 0
        int countPredicate = 0
        int countThreshold = 3

        @CircuitBreaker(attempts = '5', delay = '5ms', includes = MyCustomException.class)
        Integer getCountIncludes(boolean illegalState) {
            countIncludes++
            if(countIncludes < countThreshold) {
                if (illegalState) {
                    throw new IllegalStateException("Bad count")
                } else {
                    throw new MyCustomException()
                }
            }
            return countIncludes
        }

        @CircuitBreaker(attempts = '5', delay = '5ms', excludes = MyCustomException.class)
        Integer getCountExcludes(boolean illegalState) {
            countExcludes++
            if(countExcludes < countThreshold) {
                if (illegalState) {
                    throw new IllegalStateException("Bad count")
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

        @CircuitBreaker(attempts = '5', delay = '5ms', predicate = MyRetryPredicate.class)
        Integer getCountPredicate(boolean illegalState) {
            countPredicate++
            if(countPredicate < countThreshold) {
                if (illegalState) {
                    throw new IllegalStateException("Bad count")
                } else {
                    throw new MyCustomException()
                }
            }
            return countPredicate
        }
    }

}
