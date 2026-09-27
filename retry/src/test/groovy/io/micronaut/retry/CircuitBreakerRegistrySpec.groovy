/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.exception.CircuitOpenException
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger

class CircuitBreakerRegistrySpec extends Specification {

    void "operations of the same name share one circuit and keep their own retries"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry)
        CircuitBreakerOperations once = registry.circuitBreaker("shared", CircuitBreakerPolicy.builder().maxAttempts(1).delay(Duration.ofMillis(1)).resetTimeout(Duration.ofMinutes(1)).build())
        CircuitBreakerOperations twice = registry.circuitBreaker("shared", CircuitBreakerPolicy.builder().maxAttempts(2).delay(Duration.ofMillis(1)).build())
        AtomicInteger calls = new AtomicInteger()

        expect:
        registry.findState("shared").get() == CircuitState.CLOSED
        registry.findState("unknown").isEmpty()

        when: "one user fails with its one retry"
        once.execute { calls.incrementAndGet(); throw new IllegalStateException("down") }

        then: "the circuit of the name opens"
        thrown(IllegalStateException)
        calls.get() == 2
        once.currentState() == CircuitState.OPEN
        twice.currentState() == CircuitState.OPEN
        registry.findState("shared").get() == CircuitState.OPEN

        when: "the other user fails fast without calling"
        twice.execute { calls.incrementAndGet(); "ok" }

        then:
        IllegalStateException e = thrown()
        e.message == "down"
        calls.get() == 2

        cleanup:
        context.close()
    }

    void "a configured circuit breaker takes its reset timeout and retries from the configuration"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.retry.circuit-breakers.orders.reset'                  : '150ms',
            'micronaut.retry.circuit-breakers.orders.attempts'               : '1',
            'micronaut.retry.circuit-breakers.orders.throw-wrapped-exception': 'true'
        ])
        CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry)
        // the reset timeout of the policy is ignored: the circuit is configured
        CircuitBreakerOperations configured = registry.circuitBreaker("orders")
        CircuitBreakerOperations other = registry.circuitBreaker("orders", CircuitBreakerPolicy.builder().maxAttempts(1).resetTimeout(Duration.ofHours(1)).build())
        AtomicInteger calls = new AtomicInteger()
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect:
        registry.getNames().contains("orders")
        registry.findState("orders").get() == CircuitState.CLOSED
        registry.circuitBreaker("orders").is(configured)

        when:
        configured.executeCompletionStage { calls.incrementAndGet(); CompletableFuture.failedFuture(new IllegalStateException("down")) }.toCompletableFuture().get()

        then: "the configured attempts: one retry"
        ExecutionException e = thrown()
        e.cause instanceof IllegalStateException
        calls.get() == 2

        when: "the configuration wraps the error of the open circuit"
        configured.execute { calls.incrementAndGet(); "ok" }

        then:
        thrown(CircuitOpenException)
        calls.get() == 2

        and: "after the configured reset timeout the circuit half-opens and a success closes it"
        conditions.eventually {
            assert registry.findState("orders").get() == CircuitState.HALF_OPEN
        }
        other.execute { calls.incrementAndGet(); "ok" } == "ok"
        registry.findState("orders").get() == CircuitState.CLOSED
        configured.currentState() == CircuitState.CLOSED

        cleanup:
        context.close()
    }

    void "annotated methods with the same name share the circuit with each other and with the registry"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'CircuitBreakerRegistrySpec'])
        SharedBreakerService service = context.getBean(SharedBreakerService)
        CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry)

        when: "one method exhausts its retries"
        service.failing()

        then: "the attempt and its one retry"
        thrown(IllegalStateException)
        service.failingCalls.get() == 2
        registry.findState("inventory").get() == CircuitState.OPEN

        when: "the other method of the name fails fast"
        service.working()

        then:
        IllegalStateException e = thrown()
        e.message == "inventory down"
        service.workingCalls.get() == 0

        when: "a programmatic user of the name fails fast too"
        registry.circuitBreaker("inventory").execute { "ok" }

        then:
        thrown(IllegalStateException)

        when: "a method without a name keeps its own circuit"
        String result = service.alone()

        then:
        result == "alone"

        cleanup:
        context.close()
    }

    void "a guard reports outcomes to the shared circuit and never retries"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            'micronaut.retry.circuit-breakers.payments.reset': '150ms'
        ])
        CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry)
        CircuitBreakerGuard guard = registry.guard("payments")
        CircuitBreakerOperations operations = registry.circuitBreaker("payments")
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect:
        registry.guard("payments").is(guard)
        guard.name == "payments"
        guard.state == CircuitState.CLOSED

        when: "a success keeps it closed"
        guard.acquire().onSuccess()

        then:
        guard.state == CircuitState.CLOSED

        when: "one failure opens it, for the other users of the name too"
        guard.acquire().onFailure(new IllegalStateException("payments down"))

        then:
        guard.state == CircuitState.OPEN
        operations.currentState() == CircuitState.OPEN

        when:
        guard.acquire()

        then: "the guard always wraps the failure that opened the circuit"
        CircuitOpenException open = thrown()
        open.cause.message == "payments down"

        and: "it half-opens after the configured reset, and the first success closes it"
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }
        guard.acquire().onSuccess()
        guard.state == CircuitState.CLOSED
        registry.findState("payments").get() == CircuitState.CLOSED

        when: "a failure of the half-open circuit opens it again"
        guard.acquire().onFailure(new IllegalStateException("again"))
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }
        guard.acquire().onFailure(new IllegalStateException("still down"))

        then:
        guard.state == CircuitState.OPEN

        cleanup:
        context.close()
    }

    @Singleton
    @Requires(property = "spec.name", value = "CircuitBreakerRegistrySpec")
    static class SharedBreakerService {
        final AtomicInteger failingCalls = new AtomicInteger()
        final AtomicInteger workingCalls = new AtomicInteger()

        @CircuitBreaker(name = "inventory", attempts = "1", delay = "1ms", reset = "1m")
        String failing() {
            failingCalls.incrementAndGet()
            throw new IllegalStateException("inventory down")
        }

        @CircuitBreaker(name = "inventory", attempts = "3", delay = "1ms", reset = "1m")
        String working() {
            workingCalls.incrementAndGet()
            return "working"
        }

        @CircuitBreaker(attempts = "1", delay = "1ms", reset = "1m")
        String alone() {
            return "alone"
        }
    }
}
