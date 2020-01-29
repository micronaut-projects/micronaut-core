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
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.event.RetryEvent
import io.micronaut.retry.event.RetryEventListener
import io.reactivex.Single
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

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

    @Unroll
    void "test retry inclusive exception filtering  with #name"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        callGetCount(counterService, CountMode.THROW_ILLEGAL_STATE)

        then: "retry didn't kick in because the exception thrown doesn't match $name"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        when:
        callGetCount(counterService, mode)

        then: "retry kicks in because the exception thrown matches $name"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        cleanup:
        context.stop()

        where:
        name            | mode                  | callGetCount
        "includes"      | CountMode.DEFAULT     | { service, mode -> service.getCountIncludes(mode) }
        "includesAllOf" | CountMode.THROW_CHILD | { service, mode -> service.getCountIncludesAllOf(mode) }
    }

    void "test retry inclusive exception filtering with includes and includesAllOf"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountIncludesWithIncludesAllOf(CountMode.THROW_ILLEGAL_STATE)

        then: "retry didn't kick in because the exception thrown doesn't match includes nor includesAllOf"
        thrown(IllegalStateException)
        counterService.countIncludes == 1

        when:
        counterService.getCountIncludesWithIncludesAllOf(CountMode.DEFAULT)

        then: "retry kicks in because the exception thrown matches includes"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        when:
        counterService.countIncludes = 0
        counterService.getCountIncludesWithIncludesAllOf(CountMode.THROW_CHILD)

        then: "retry kicks in because the exception thrown matches includesAllOf"
        noExceptionThrown()
        counterService.countIncludes == counterService.countThreshold

        cleanup:
        context.stop()
    }

    @Unroll
    void "test retry exclusive exception filtering with #name"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        callGetCount(counterService, mode)

        then: "retry didn't kick in because the exception thrown matches $name"
        thrown(expectedException)
        counterService.countExcludes == 1

        when:
        callGetCount(counterService, CountMode.THROW_ILLEGAL_STATE)

        then: "retry kicks in because the exception thrown doesn't match $name"
        noExceptionThrown()
        counterService.countExcludes == counterService.countThreshold

        cleanup:
        context.stop()

        where:
        name            | mode                  | expectedException      | callGetCount
        "excludes"      | CountMode.DEFAULT     | MyCustomException      | { service, mode -> service.getCountExcludes(mode) }
        "excludesAllOf" | CountMode.THROW_CHILD | MyCustomChildException | { service, mode -> service.getCountExcludesAllOf(mode) }
    }

    void "test retry exclusive exception filtering with excludes and excludesAllOf"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CounterService counterService = context.getBean(CounterService)

        when:
        counterService.getCountExcludesWithExcludesAllOf(CountMode.DEFAULT)

        then: "retry didn't kick in because the exception thrown matches excludes"
        thrown(MyCustomException)
        counterService.countExcludes == 1

        when:
        counterService.getCountExcludesWithExcludesAllOf(CountMode.THROW_CHILD)

        then: "retry didn't kick in because the exception thrown matches excludesAllOf"
        thrown(MyCustomChildException)
        counterService.countExcludes == 2

        when:
        counterService.getCountExcludesWithExcludesAllOf(CountMode.THROW_ILLEGAL_STATE)

        then: "retry kicks in because the exception thrown doesn't match excludes nor excludesAllOf"
        noExceptionThrown()
        counterService.countExcludes == counterService.countThreshold

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

    static enum CountMode {
        THROW_ILLEGAL_STATE,
        THROW_CHILD,
        DEFAULT
    }

    @Singleton
    static class CounterService {
        int count = 0
        int countRx = 0
        int countReact = 0
        int countIncludes = 0
        int countExcludes = 0
        int countCompletion = 0
        int _countCompletionStage = 0
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

        @Retryable(attempts = '5', delay = '5ms', includes = MyCustomException.class)
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

        @Retryable(attempts = '5', delay = '5ms', includesAllOf = MyCustomBaseException.class)
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

        @Retryable(attempts = '5', delay = '5ms', includes = MyCustomException.class, includesAllOf = MyCustomBaseException.class)
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

        @Retryable(attempts = '5', delay = '5ms', excludes = MyCustomException.class)
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

        @Retryable(attempts = '5', delay = '5ms', excludesAllOf = MyCustomBaseException.class)
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

        @Retryable(attempts = '5', delay = '5ms', excludes = MyCustomException.class, excludesAllOf = MyCustomBaseException.class)
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
    }
}
