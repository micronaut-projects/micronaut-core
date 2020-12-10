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
        int result = counterService.getCountSync()

        then:"It executes until successful"
        result == 3
        listener.events.size() == 2

        when:"The threshold can never be met"
        listener.reset()
        counterService.countThreshold = 10
        counterService.count = 0
        counterService.getCountSync()

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

    void "test simply retry with rxjava pre errors"() {
        given:
            ApplicationContext context = ApplicationContext.run()
            CounterService counterService = context.getBean(CounterService)
            MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
            int result = counterService.getCountSingleRxPreErrors().blockingGet()

        then:"It executes until successful"
            listener.events.size() == 4
            result == 3

        when:"The threshold can never be met"
            listener.reset()
            counterService.countThreshold = 10
            counterService.countRx = 0
            counterService.count = 0
            counterService.getCountSingleRxPreErrors().blockingGet()

        then:"The original exception is thrown"
            def e = thrown(IllegalStateException)
            e.message == "Bad count"

        when:"Pre throws error"
            listener.reset()
            counterService.countPreThreshold = 10
            counterService.countPreRx = 0
            counterService.count = 0
            counterService.getCountSingleRxPreErrors().blockingGet()

        then:"The original exception is thrown"
            def ex = thrown(IllegalStateException)
            ex.message == "Bad pre count"

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

    void "test simply retry with completion stage pre errors"() {
        given:
            ApplicationContext context = ApplicationContext.run()
            CounterService counterService = context.getBean(CounterService)
            MyRetryListener listener = context.getBean(MyRetryListener)

        when:"A method is annotated retry"
            int result = counterService.getCountCompletionStagePreErrors().toCompletableFuture().get()

        then:"It executes until successful"
            listener.events.size() == 4
            result == 3

        when:"The threshold can never be met"
            listener.reset()
            counterService.countThreshold = 10
            counterService._countCompletionStage = 0
            counterService.getCountCompletionStagePreErrors().toCompletableFuture().get()

        then:"The original exception is thrown"
            def e = thrown(ExecutionException)
            e.cause.message == "Bad count"

        when:"Pre throws error"
            listener.reset()
            counterService.countPreThreshold = 10
            counterService.countPreCompletionStage = 0
            counterService.getCountCompletionStagePreErrors().toCompletableFuture().get()

        then:"The original exception is thrown"
            def ex = thrown(ExecutionException)
            ex.cause.message == "Bad pre count"

        cleanup:
            context.stop()
    }


    void "test retry with includes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountIncludes(true)

        then: "retry didn't kick in because the exception thrown doesn't match includes"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        when:
        counterService.getCountIncludes(false)

        then: "retry kicks in because the exception thrown matches includes"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        cleanup:
        context.stop()
    }

    void "test retry with excludes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountExcludes(false)

        then: "retry didn't kick in because the exception thrown matches excludes"
        thrown(MyCustomException)
        counterService.countExcludes == 1

        when:
        counterService.getCountExcludes(true)

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
        int countPreRx = 0
        int countRx = 0
        int countReact = 0
        int countIncludes = 0
        int countExcludes = 0
        int countPreCompletion = 0
        int countCompletion = 0
        int _countCompletionStage = 0
        int countPreCompletionStage = 0
        int countPredicate = 0
        int countThreshold = 3
        int countPreThreshold = 3

        @Retryable(attempts = '5', delay = '5ms')
        int getCountSync() {
            count++
            if(count < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return count
        }

        @Retryable(attempts = '5', delay = '5ms')
        Single<Integer> getCountSingle() {
            Single.fromCallable({->
                count++
                if(count < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return count
            })
        }

        @Retryable(attempts = '7', delay = '5ms')
        Single<Integer> getCountSingleRxPreErrors() {
            countPreRx++
            if(countPreRx < countPreThreshold) {
                throw new IllegalStateException("Bad pre count")
            }
            Single.fromCallable({->
                countRx++
                if(countRx < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return countRx
            })
        }

        @Retryable(attempts = '5', delay = '5ms', includes = MyCustomException.class)
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

        @Retryable(attempts = '5', delay = '5ms', excludes = MyCustomException.class)
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

        @Retryable(attempts = '7', delay = '5ms')
        CompletionStage<Integer> getCountCompletionStagePreErrors() {
            countPreCompletionStage++
            if(countPreCompletionStage < countPreThreshold) {
                throw new IllegalStateException("Bad pre count")
            }
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
