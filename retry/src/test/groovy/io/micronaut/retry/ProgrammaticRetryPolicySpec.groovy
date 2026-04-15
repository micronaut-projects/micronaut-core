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

import io.micronaut.retry.annotation.DefaultRetryPredicate
import io.micronaut.retry.annotation.RetryPredicate
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.function.Supplier

class ProgrammaticRetryPolicySpec extends Specification {

    void "retry policy builder defaults match retryable defaults"() {
        when:
        RetryPolicy policy = RetryPolicy.builder().build()

        then:
        policy.maxAttempts == 3
        policy.delay == Duration.ofSeconds(1)
        !policy.getMaxDelay().present
        policy.multiplier == 1.0d
        policy.jitter == 0.0d
        policy.capturedException == RuntimeException
        policy.predicate instanceof DefaultRetryPredicate
    }

    void "circuit breaker policy builder defaults match annotation defaults"() {
        when:
        CircuitBreakerPolicy policy = CircuitBreakerPolicy.builder().build()

        then:
        policy.maxAttempts == 3
        policy.delay == Duration.ofMillis(500)
        policy.maxDelay.get() == Duration.ofSeconds(5)
        policy.multiplier == 0.0d
        policy.jitter == 0.0d
        policy.capturedException == RuntimeException
        policy.resetTimeout == Duration.ofSeconds(20)
        !policy.throwWrappedException
        policy.predicate instanceof DefaultRetryPredicate
    }

    void "circuit breaker operations extends retry operations"() {
        expect:
        RetryOperations.isAssignableFrom(CircuitBreakerOperations)
    }

    void "factories create reusable operations instances"() {
        given:
        RetryOperationsFactory retryOperationsFactory = Mock()
        CircuitBreakerOperationsFactory circuitBreakerOperationsFactory = Mock()
        RetryPolicy retryPolicy = RetryPolicy.builder().build()
        CircuitBreakerPolicy circuitBreakerPolicy = CircuitBreakerPolicy.builder().build()
        RetryOperations retryOperations = Mock()
        CircuitBreakerOperations circuitBreakerOperations = Mock()

        when:
        RetryOperations createdRetryOperations = retryOperationsFactory.createRetryOperations(retryPolicy)
        CircuitBreakerOperations createdCircuitBreakerOperations = circuitBreakerOperationsFactory.createCircuitBreakerOperations(circuitBreakerPolicy)

        then:
        1 * retryOperationsFactory.createRetryOperations(retryPolicy) >> retryOperations
        1 * circuitBreakerOperationsFactory.createCircuitBreakerOperations(circuitBreakerPolicy) >> circuitBreakerOperations
        createdRetryOperations.is(retryOperations)
        createdCircuitBreakerOperations.is(circuitBreakerOperations)
    }

    void "operations contracts are supplier based"() {
        given:
        RetryOperations retryOperations = Stub() {
            execute(_ as Supplier<String>) >> "value"
            executeCompletionStage(_ as Supplier<CompletionStage<String>>) >> Stub(CompletionStage)
            executePublisher(_ as Supplier) >> null
        }

        expect:
        retryOperations.execute({ "value" }) == "value"
        retryOperations.executeCompletionStage({ throw new UnsupportedOperationException("stub") }) != null
        retryOperations.executePublisher({ null }) == null
    }

    void "explicit predicate is preserved on retry policy"() {
        given:
        RetryPredicate predicate = { throwable -> throwable instanceof IllegalStateException } as RetryPredicate

        when:
        RetryPolicy policy = RetryPolicy.builder()
            .includes(RuntimeException)
            .excludes(IllegalArgumentException)
            .predicate(predicate)
            .build()

        then:
        policy.predicate.is(predicate)
    }
}
