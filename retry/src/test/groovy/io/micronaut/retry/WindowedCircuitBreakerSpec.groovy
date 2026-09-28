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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Circuit breakers with a rolling window, as in MicroProfile Fault Tolerance.
 */
class WindowedCircuitBreakerSpec extends Specification {

    ApplicationContext context
    CircuitBreakerRegistry registry
    PollingConditions conditions = new PollingConditions(timeout: 5)

    def setup() {
        context = ApplicationContext.run([
            'spec.name': 'WindowedCircuitBreakerSpec',
            'micronaut.retry.circuit-breakers.window.request-volume-threshold': 4,
            'micronaut.retry.circuit-breakers.window.failure-ratio': 0.5,
            'micronaut.retry.circuit-breakers.window.success-threshold': 2,
            'micronaut.retry.circuit-breakers.window.reset': '200ms',
            'micronaut.retry.circuit-breakers.classified.request-volume-threshold': 2,
            'micronaut.retry.circuit-breakers.classified.failure-ratio': 1,
            'micronaut.retry.circuit-breakers.classified.fail-on': ['java.lang.RuntimeException'],
            'micronaut.retry.circuit-breakers.classified.skip-on': ['java.util.ConcurrentModificationException'],
            'micronaut.retry.circuit-breakers.concurrent.request-volume-threshold': 10,
            'micronaut.retry.circuit-breakers.concurrent.success-threshold': 5,
            'micronaut.retry.circuit-breakers.concurrent.failure-ratio': 0.1,
            'micronaut.retry.circuit-breakers.concurrent.reset': '2s',
        ])
        registry = context.getBean(CircuitBreakerRegistry)
    }

    def cleanup() {
        context.close()
    }

    private static void fail(CircuitBreakerGuard guard, Throwable failure = new IllegalStateException("down")) {
        guard.acquire().onFailure(failure)
    }

    private static void succeed(CircuitBreakerGuard guard) {
        guard.acquire().onSuccess()
    }

    void "the circuit never opens before the window is full, and opens when the failure ratio is reached"() {
        given:
        CircuitBreakerGuard guard = registry.guard("window")

        when: "three calls, two failures: the window of four is not full"
        fail(guard)
        fail(guard)
        succeed(guard)

        then:
        guard.state == CircuitState.CLOSED
        registry.findSnapshot("window").get().calls() == 3
        registry.findSnapshot("window").get().failures() == 2

        when: "the fourth call fills the window: two failures of four reach the ratio 0.5"
        succeed(guard)

        then:
        guard.state == CircuitState.OPEN
        registry.findSnapshot("window").get().openedCount() == 1

        when:
        guard.acquire()

        then: "the guard wraps the last failure of the window"
        CircuitOpenException open = thrown()
        open.cause.message == "down"
    }

    void "the window rolls: old outcomes leave it"() {
        given:
        CircuitBreakerGuard guard = registry.guard("window")

        when: "one failure, then successes: the failure rolls out of the window"
        fail(guard)
        5.times { succeed(guard) }
        fail(guard)

        then: "one failure in the last four calls"
        guard.state == CircuitState.CLOSED
        registry.findSnapshot("window").get().failures() == 1

        when:
        fail(guard)

        then:
        guard.state == CircuitState.OPEN
    }

    void "a half-open circuit permits its trial calls, rejects the others without counting them, and closes when the trials succeed"() {
        given:
        CircuitBreakerGuard guard = registry.guard("window")
        4.times { fail(guard) }

        expect:
        guard.state == CircuitState.OPEN
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }

        when: "two trial permits"
        CircuitBreakerGuard.Permit first = guard.acquire()
        CircuitBreakerGuard.Permit second = guard.acquire()
        guard.acquire()

        then: "a third call is rejected"
        CircuitOpenException rejected = thrown()
        rejected.message.contains("Half-Open")
        registry.findSnapshot("window").get().trials() == 2

        when:
        first.onSuccess()

        then:
        guard.state == CircuitState.HALF_OPEN
        registry.findSnapshot("window").get().successes() == 1

        when:
        second.onSuccess()

        then: "closed with a new, empty window"
        guard.state == CircuitState.CLOSED
        registry.findSnapshot("window").get().calls() == 0
    }

    void "any failure of a half-open circuit opens it again"() {
        given:
        CircuitBreakerGuard guard = registry.guard("window")
        4.times { fail(guard) }
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }

        when:
        CircuitBreakerGuard.Permit first = guard.acquire()
        CircuitBreakerGuard.Permit second = guard.acquire()
        first.onSuccess()
        second.onFailure(new IllegalStateException("still down"))

        then:
        guard.state == CircuitState.OPEN
        registry.findSnapshot("window").get().openedCount() == 2
    }

    void "an outcome of a call of an older state counts for nothing"() {
        given:
        CircuitBreakerGuard guard = registry.guard("window")
        CircuitBreakerGuard.Permit late = guard.acquire()
        4.times { fail(guard) }
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }

        when: "the late call of the closed circuit ends while the circuit is half-open"
        late.onFailure(new IllegalStateException("late"))

        then:
        guard.state == CircuitState.HALF_OPEN
    }

    void "skipOn counts as a success whatever failOn says, and an exception that is not failOn is a success"() {
        given:
        CircuitBreakerGuard guard = registry.guard("classified")

        when: "a RuntimeException that skipOn names, and an exception that failOn does not name"
        fail(guard, new ConcurrentModificationException("skipped"))
        fail(guard, new IOException("not failOn"))

        then:
        guard.state == CircuitState.CLOSED
        registry.findSnapshot("classified").get().failures() == 0

        when:
        fail(guard, new IllegalStateException("counted"))
        fail(guard, new IllegalStateException("counted"))

        then:
        guard.state == CircuitState.OPEN
    }

    void "exactly the permitted trial calls pass a half-open circuit under concurrent calls"() {
        given:
        CircuitBreakerGuard guard = registry.guard("concurrent")
        10.times { fail(guard) }
        def executor = Executors.newFixedThreadPool(32)
        CountDownLatch start = new CountDownLatch(1)
        AtomicInteger permitted = new AtomicInteger()
        AtomicInteger rejected = new AtomicInteger()
        List<CircuitBreakerGuard.Permit> permits = Collections.synchronizedList([])
        // the calls wait before the circuit half-opens: trials that take longer than the reset get new permits
        200.times {
            executor.submit {
                start.await()
                try {
                    permits << guard.acquire()
                    permitted.incrementAndGet()
                } catch (CircuitOpenException e) {
                    rejected.incrementAndGet()
                }
            }
        }
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }

        when:
        start.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        then:
        permitted.get() == 5
        rejected.get() == 195
        registry.findSnapshot("concurrent").get().trials() == 5

        when: "the trials succeed concurrently"
        def closing = Executors.newFixedThreadPool(5)
        permits.each { permit -> closing.submit { permit.onSuccess() } }
        closing.shutdown()
        closing.awaitTermination(10, TimeUnit.SECONDS)

        then:
        guard.state == CircuitState.CLOSED
    }

    void "concurrent outcomes of a closed circuit keep the window consistent"() {
        given:
        CircuitBreakerGuard guard = registry.guard("concurrent")
        def executor = Executors.newFixedThreadPool(16)

        when: "many successes from many threads"
        1000.times { executor.submit { succeed(guard) } }
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        CircuitBreakerSnapshot snapshot = registry.findSnapshot("concurrent").get()

        then:
        guard.state == CircuitState.CLOSED
        snapshot.calls() == 10
        snapshot.failures() == 0
        snapshot.requestVolumeThreshold() == 10
    }

    void "a policy with a window, programmatic and annotated"() {
        given:
        CircuitBreakerOperations operations = registry.circuitBreaker("programmatic", CircuitBreakerPolicy.builder()
            .maxAttempts(1).delay(Duration.ofMillis(1)).resetTimeout(Duration.ofMinutes(1))
            .requestVolumeThreshold(3).failureRatio(0.6).build())
        WindowedService service = context.getBean(WindowedService)

        when: "two failed operations of three: 0.67 reaches 0.6"
        2.times {
            try {
                operations.execute { throw new IllegalStateException("down") }
            } catch (IllegalStateException ignored) {
            }
        }
        operations.execute { "ok" }

        then:
        operations.currentState() == CircuitState.OPEN

        when: "an annotated method with a window of two and a ratio of 1"
        service.call(true)

        then: "the attempt and its one retry"
        thrown(IllegalStateException)
        service.calls.get() == 2

        when: "a success fills the window with one failure of two: below the ratio"
        service.call(false)

        then:
        registry.findState("annotated").get() == CircuitState.CLOSED

        when:
        2.times {
            try {
                service.call(true)
            } catch (IllegalStateException ignored) {
            }
        }
        service.call(false)

        then:
        thrown(IllegalStateException)
        registry.findState("annotated").get() == CircuitState.OPEN
    }

    void "a circuit without a window keeps the circuit breaker of Micronaut"() {
        given:
        CircuitBreakerGuard guard = registry.guard("plain")

        when: "one failure opens it"
        fail(guard)

        then:
        guard.state == CircuitState.OPEN
        registry.findSnapshot("plain").get().requestVolumeThreshold() == 0
        registry.findSnapshot("plain").get().openedCount() == 1
    }

    void "an invalid window is rejected"() {
        when:
        CircuitBreakerPolicy.builder().requestVolumeThreshold(0).build()

        then:
        thrown(IllegalArgumentException)

        when:
        CircuitBreakerPolicy.builder().failureRatio(1.5).build()

        then:
        thrown(IllegalArgumentException)

        when:
        CircuitBreakerPolicy.builder().successThreshold(0).build()

        then:
        thrown(IllegalArgumentException)
    }

    @Singleton
    @Requires(property = "spec.name", value = "WindowedCircuitBreakerSpec")
    static class WindowedService {
        final AtomicInteger calls = new AtomicInteger()

        @CircuitBreaker(name = "annotated", attempts = "1", delay = "1ms", reset = "1m", requestVolumeThreshold = "2", failureRatio = "1")
        String call(boolean fail) {
            calls.incrementAndGet()
            if (fail) {
                throw new IllegalStateException("down")
            }
            return "ok"
        }
    }
}
