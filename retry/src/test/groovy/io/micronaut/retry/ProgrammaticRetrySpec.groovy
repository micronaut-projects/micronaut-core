/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.RetryPredicate
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

class ProgrammaticRetrySpec extends Specification {

    void "programmatic retry supports synchronous flows"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RetryOperationsFactory factory = context.getBean(RetryOperationsFactory)
        RetryOperations operations = factory.createRetryOperations(
            RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).build()
        )
        AtomicInteger counter = new AtomicInteger()

        when:
        int result = operations.execute {
            int current = counter.incrementAndGet()
            if (current < 3) {
                throw new IllegalStateException("Bad count")
            }
            return current
        }

        then:
        result == 3
        counter.get() == 3

        cleanup:
        context.close()
    }

    void "programmatic retry supports publisher flows and retries fresh publishers"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RetryOperationsFactory factory = context.getBean(RetryOperationsFactory)
        RetryOperations operations = factory.createRetryOperations(
            RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).build()
        )
        AtomicInteger counter = new AtomicInteger()

        when:
        int result = Mono.from(operations.executePublisher {
            Mono.fromCallable {
                int current = counter.incrementAndGet()
                if (current < 3) {
                    throw new IllegalStateException("Bad count")
                }
                return current
            }
        }).block()

        then:
        result == 3
        counter.get() == 3

        cleanup:
        context.close()
    }

    void "programmatic retry supports completion stage flows"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RetryOperationsFactory factory = context.getBean(RetryOperationsFactory)
        RetryOperations operations = factory.createRetryOperations(
            RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).build()
        )
        AtomicInteger counter = new AtomicInteger()

        when:
        int result = operations.executeCompletionStage {
            CompletableFuture.supplyAsync {
                int current = counter.incrementAndGet()
                if (current < 3) {
                    throw new IllegalStateException("Bad count")
                }
                return current
            }
        }.toCompletableFuture().get()

        then:
        result == 3
        counter.get() == 3

        cleanup:
        context.close()
    }

    void "programmatic retry supports pre-stage and pre-publisher failures"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RetryOperationsFactory factory = context.getBean(RetryOperationsFactory)
        RetryOperations operations = factory.createRetryOperations(
            RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).build()
        )
        AtomicInteger publisherCounter = new AtomicInteger()
        AtomicInteger stageCounter = new AtomicInteger()

        when:
        int publisherResult = Mono.from(operations.executePublisher {
            int current = publisherCounter.incrementAndGet()
            if (current < 3) {
                throw new IllegalStateException("Bad publisher")
            }
            return Mono.just(current)
        }).block()
        int stageResult = operations.executeCompletionStage {
            int current = stageCounter.incrementAndGet()
            if (current < 3) {
                throw new IllegalStateException("Bad stage")
            }
            return CompletableFuture.completedFuture(current)
        }.toCompletableFuture().get()

        then:
        publisherResult == 3
        stageResult == 3

        cleanup:
        context.close()
    }

    void "programmatic retry honors includes excludes predicate and captured exception"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RetryOperationsFactory factory = context.getBean(RetryOperationsFactory)
        RetryPredicate predicate = { failure -> failure instanceof MyProgrammaticException } as RetryPredicate

        when:
        factory.createRetryOperations(RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).includes(MyProgrammaticException).build())
            .execute(new ThrowingSupplier(new IllegalStateException("bad")))

        then:
        thrown(IllegalStateException)

        when:
        factory.createRetryOperations(RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).excludes(MyProgrammaticException).build())
            .execute(new ThrowingSupplier(new MyProgrammaticException("bad")))

        then:
        thrown(MyProgrammaticException)

        when:
        factory.createRetryOperations(RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).predicate(predicate).build())
            .execute(new ThrowingSupplier(new IllegalStateException("bad")))

        then:
        thrown(IllegalStateException)

        when:
        factory.createRetryOperations(RetryPolicy.builder().maxAttempts(5).delay(Duration.ofMillis(5)).capturedException(Throwable).build())
            .execute {
                throw new Throwable("captured")
            }

        then:
        Throwable throwable = thrown()
        throwable.cause?.message == 'captured' || throwable.message == 'captured'

        cleanup:
        context.close()
    }

    void "programmatic retry exhausts with original failure"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RetryOperationsFactory factory = context.getBean(RetryOperationsFactory)
        RetryOperations operations = factory.createRetryOperations(
            RetryPolicy.builder().maxAttempts(3).delay(Duration.ofMillis(5)).build()
        )

        when:
        operations.execute {
            throw new IllegalStateException("still bad")
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'still bad'

        when:
        operations.executeCompletionStage {
            CompletableFuture.failedFuture(new IllegalStateException("still bad async"))
        }.toCompletableFuture().get()

        then:
        ExecutionException executionException = thrown()
        executionException.cause.message == 'still bad async'

        cleanup:
        context.close()
    }

    static final class ThrowingSupplier implements java.util.function.Supplier<Integer> {
        private final RuntimeException runtimeException

        ThrowingSupplier(RuntimeException runtimeException) {
            this.runtimeException = runtimeException
        }

        @Override
        Integer get() {
            throw runtimeException
        }
    }

    static final class MyProgrammaticException extends RuntimeException {
        MyProgrammaticException(String message) {
            super(message)
        }
    }
}
