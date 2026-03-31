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
import io.micronaut.retry.exception.CircuitOpenException
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

class ProgrammaticCircuitBreakerSpec extends Specification {

    void "programmatic circuit breaker shares state across invocations"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerOperationsFactory factory = context.getBean(CircuitBreakerOperationsFactory)
        CircuitBreakerOperations operations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(2).delay(Duration.ofMillis(5)).resetTimeout(Duration.ofMillis(100)).build()
        )
        AtomicInteger counter = new AtomicInteger()

        when:
        operations.execute {
            counter.incrementAndGet()
            throw new IllegalStateException('boom')
        }

        then:
        thrown(IllegalStateException)
        operations.currentState() == CircuitState.OPEN
        counter.get() == 3

        when:
        operations.execute {
            counter.incrementAndGet()
            return counter.get()
        }

        then:
        thrown(IllegalStateException)
        counter.get() == 3

        cleanup:
        context.close()
    }

    void "programmatic circuit breaker transitions from open to half open to closed"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerOperationsFactory factory = context.getBean(CircuitBreakerOperationsFactory)
        CircuitBreakerOperations operations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(2).delay(Duration.ofMillis(5)).resetTimeout(Duration.ofMillis(100)).build()
        )
        AtomicInteger counter = new AtomicInteger()
        PollingConditions conditions = new PollingConditions(timeout: 3)

        when:
        operations.execute {
            counter.incrementAndGet()
            throw new IllegalStateException('boom')
        }

        then:
        thrown(IllegalStateException)
        operations.currentState() == CircuitState.OPEN

        when:
        conditions.eventually {
            operations.currentState() == CircuitState.HALF_OPEN
        }
        int result = operations.execute {
            counter.incrementAndGet()
            return counter.get()
        }

        then:
        result == 4
        operations.currentState() == CircuitState.CLOSED

        cleanup:
        context.close()
    }

    void "programmatic circuit breaker supports publisher and completion stage flows"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerOperationsFactory factory = context.getBean(CircuitBreakerOperationsFactory)
        CircuitBreakerOperations publisherOperations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(3).delay(Duration.ofMillis(5)).resetTimeout(Duration.ofMillis(100)).build()
        )
        CircuitBreakerOperations completionStageOperations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(3).delay(Duration.ofMillis(5)).resetTimeout(Duration.ofMillis(100)).build()
        )
        AtomicInteger publisherCounter = new AtomicInteger()
        AtomicInteger stageCounter = new AtomicInteger()

        when:
        int publisherResult = Mono.from(publisherOperations.executePublisher {
            Mono.fromCallable {
                int current = publisherCounter.incrementAndGet()
                if (current < 3) {
                    throw new IllegalStateException('bad publisher')
                }
                return current
            }
        }).block()
        int stageResult = completionStageOperations.executeCompletionStage {
            CompletableFuture.supplyAsync {
                int current = stageCounter.incrementAndGet()
                if (current < 3) {
                    throw new IllegalStateException('bad stage')
                }
                return current
            }
        }.toCompletableFuture().get()

        then:
        publisherResult == 3
        stageResult == 3

        cleanup:
        context.close()
    }

    void "programmatic circuit breaker can throw wrapped exceptions"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerOperationsFactory factory = context.getBean(CircuitBreakerOperationsFactory)
        CircuitBreakerOperations operations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder()
                .maxAttempts(2)
                .delay(Duration.ofMillis(5))
                .resetTimeout(Duration.ofMillis(100))
                .throwWrappedException(true)
                .build()
        )

        when:
        operations.execute {
            throw new IllegalStateException('boom')
        }

        then:
        thrown(IllegalStateException)

        when:
        operations.execute {
            1
        }

        then:
        CircuitOpenException exception = thrown()
        exception.cause instanceof IllegalStateException

        cleanup:
        context.close()
    }

    void "programmatic circuit breaker honors predicate and excludes"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerOperationsFactory factory = context.getBean(CircuitBreakerOperationsFactory)
        RetryPredicate predicate = { failure -> failure instanceof MyProgrammaticException } as RetryPredicate
        CircuitBreakerOperations predicateOperations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(2).delay(Duration.ofMillis(5)).predicate(predicate).build()
        )
        CircuitBreakerOperations excludeOperations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(2).delay(Duration.ofMillis(5)).excludes(MyProgrammaticException).build()
        )

        when:
        predicateOperations.execute {
            throw new IllegalStateException('boom')
        }

        then:
        thrown(IllegalStateException)
        predicateOperations.currentState() == CircuitState.CLOSED

        when:
        excludeOperations.execute {
            throw new MyProgrammaticException('boom')
        }

        then:
        thrown(MyProgrammaticException)
        excludeOperations.currentState() == CircuitState.CLOSED

        cleanup:
        context.close()
    }

    void "programmatic circuit breaker preserves async terminal failures"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerOperationsFactory factory = context.getBean(CircuitBreakerOperationsFactory)
        CircuitBreakerOperations operations = factory.createCircuitBreakerOperations(
            CircuitBreakerPolicy.builder().maxAttempts(2).delay(Duration.ofMillis(5)).build()
        )

        when:
        operations.executeCompletionStage {
            CompletableFuture.failedFuture(new IllegalStateException('bad async'))
        }.toCompletableFuture().get()

        then:
        ExecutionException exception = thrown()
        exception.cause.message == 'bad async'
        operations.currentState() == CircuitState.OPEN

        cleanup:
        context.close()
    }

    static final class MyProgrammaticException extends RuntimeException {
        MyProgrammaticException(String message) {
            super(message)
        }
    }
}
