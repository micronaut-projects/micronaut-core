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
import io.micronaut.retry.annotation.RetryPredicate
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import io.reactivex.Single
import spock.lang.Specification

import javax.inject.Singleton
import javax.persistence.OptimisticLockException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

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

    void "test simply retry with completablefuture"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
        int result = counterService.getCountCompletable().get()


        then:"It executes until successful"
        listener.events.size() == 2
        result == 3

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.count = 0
        def single = counterService.getCountCompletable()
        single.get()

        then:"The original exception is thrown"
        def e = thrown(ExecutionException)
        e.cause.message == "Bad count"

        cleanup:
        context.stop()
    }

    void "test simply retry with completion stage"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)
        MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
        int result = counterService.getCountCompletionStage().toCompletableFuture().get()


        then:"It executes until successful"
        listener.events.size() == 2
        result == 3

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.count = 0
        def single = counterService.getCountCompletionStage()
        single.toCompletableFuture().get()

        then:"The original exception is thrown"
        def e = thrown(ExecutionException)
        e.cause.message == "Bad count"

        cleanup:
        context.stop()
    }

    void "test retry with includes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.updateCountIncludes(true)

        then: "retry didn't kick in because the exception thrown doesn't match includes"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        when:
        counterService.updateCountIncludes(false)

        then: "retry kicks in because the exception thrown matches includes"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        when:
        counterService.updateCountUnresolveableIncludes()

        then: "retry didn't kick in because the exception thrown does not match includes that can not be resolved"
        thrown(IllegalStateException)
        counterService.countUnresolveableIncludes == 1

        cleanup:
        context.stop()
    }

    void "test retry with excludes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.updateCountExcludes(false)

        then: "retry didn't kick in because the exception thrown matches excludes"
        thrown(MyCustomException)
        counterService.countExcludes == 1

        when:
        counterService.updateCountExcludes(true)

        then: "retry kicks in because the exception thrown doesn't match excludes"
        noExceptionThrown()
        counterService.countExcludes == counterService.countThreshold

        cleanup:
        context.stop()
    }

    void "test retry with predicate"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountPredicate(true)

        then: "retry didn't kick in because the exception thrown doesn't match predicate"
        thrown(IllegalStateException)
        counterService.countPredicate == 1

        when:
        counterService.getCountPredicate(false)

        then: "retry kicks in because the exception thrown matches predicate"
        noExceptionThrown()
        counterService.countPredicate == counterService.countThreshold

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

    static class MyRetryPredicate implements RetryPredicate {
        @Override
        boolean test(Throwable throwable) {
            return throwable instanceof MyCustomException
        }
    }

    @Singleton
    static class CounterService {
        int count = 0
        int countRx = 0
        int countIncludes = 0
        int countUnresolveableIncludes = 0
        int countExcludes = 0
        int countCompletion = 0
        int _countCompletionStage = 0
        int countPredicate = 0
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

        @Retryable(attempts = '5', delay = '5ms', includes = MyCustomException.class)
        Integer updateCountIncludes(boolean illegalState) {
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

        @Retryable(attempts = '5', delay = '5ms', includes = OptimisticLockException.class)
        Integer updateCountUnresolveableIncludes() {
            countUnresolveableIncludes++
            if(countUnresolveableIncludes < countThreshold) {
                throw new IllegalStateException('Bad count')
            }
            return countUnresolveableIncludes
        }

        @Retryable(attempts = '5', delay = '5ms', excludes = MyCustomException.class)
        Integer updateCountExcludes(boolean illegalState) {
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

        @Retryable(attempts = '5', delay = '5ms')
        CompletableFuture<Integer> getCountCompletable() {
            CompletableFuture.supplyAsync({ ->
                countCompletion++
                if(countCompletion < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return countCompletion
            })
        }

        @Retryable(attempts = '5', delay = '5ms')
        CompletionStage<Integer> getCountCompletionStage() {
            CompletableFuture.supplyAsync({ ->
                _countCompletionStage++
                if(_countCompletionStage < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return _countCompletionStage
            })
        }

        @Retryable(attempts = '5', delay = '5ms', predicate = MyRetryPredicate.class)
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
