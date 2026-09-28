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
import io.micronaut.retry.event.CircuitClosedEvent
import io.micronaut.retry.event.CircuitOpenEvent
import io.micronaut.retry.exception.CircuitOpenException
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The calls of circuit breakers with a rolling window: each call holds its own permit, and
 * reports one outcome, or none.
 */
class WindowedCircuitBreakerCallSpec extends Specification {

    ApplicationContext context
    CircuitBreakerRegistry registry
    CallService service
    PollingConditions conditions = new PollingConditions(timeout: 5)

    def setup() {
        context = ApplicationContext.run([
            'spec.name'                                                          : 'WindowedCircuitBreakerCallSpec',
            'micronaut.retry.circuit-breakers.guarded.request-volume-threshold'  : 1,
            'micronaut.retry.circuit-breakers.guarded.reset'                     : '300ms',
            'micronaut.retry.circuit-breakers.configured.request-volume-threshold': 2,
            'micronaut.retry.circuit-breakers.configured.failure-ratio'          : 1,
            'micronaut.retry.circuit-breakers.configured-reset.reset'            : '1m',
            'micronaut.retry.circuit-breakers.checked.attempts'                  : 1,
            'micronaut.retry.circuit-breakers.checked.delay'                     : '1ms',
            'micronaut.retry.circuit-breakers.events-window.request-volume-threshold': 1,
        ])
        registry = context.getBean(CircuitBreakerRegistry)
        service = context.getBean(CallService)
    }

    def cleanup() {
        context.close()
    }

    private void failStale(int times) {
        times.times {
            try {
                service.stale(null, null, true)
            } catch (IllegalStateException ignored) {
            }
        }
    }

    void "an annotated call that started while the circuit was closed does not close it once it half-opened"() {
        given: "a call that started while the circuit was closed"
        CountDownLatch slowEntered = new CountDownLatch(1)
        CountDownLatch slowRelease = new CountDownLatch(1)
        Thread slow = Thread.start { service.stale(slowEntered, slowRelease, false) }
        slowEntered.await(5, TimeUnit.SECONDS)

        when: "two failures fill the window of two"
        failStale(2)

        then:
        registry.findState("stale").get() == CircuitState.OPEN

        when: "the circuit half-opens and a trial call takes the only trial permit"
        Thread.sleep(300)
        CountDownLatch trialEntered = new CountDownLatch(1)
        CountDownLatch trialRelease = new CountDownLatch(1)
        Thread trial = Thread.start { service.stale(trialEntered, trialRelease, false) }
        trialEntered.await(5, TimeUnit.SECONDS)

        and: "the call of the closed circuit ends with a success"
        slowRelease.countDown()
        slow.join(5000)

        then: "its outcome counts for nothing: the trial is still running"
        registry.findSnapshot("stale").get().state() == CircuitState.HALF_OPEN
        registry.findSnapshot("stale").get().trials() == 1

        when: "the trial succeeds"
        trialRelease.countDown()
        trial.join(5000)

        then:
        registry.findState("stale").get() == CircuitState.CLOSED
    }

    void "a programmatic call that started while the circuit was closed does not close it once it half-opened"() {
        given:
        CircuitBreakerOperations operations = registry.circuitBreaker("stale-operations", CircuitBreakerPolicy.builder()
            .maxAttempts(1).delay(Duration.ofMillis(1)).resetTimeout(Duration.ofMillis(200))
            .requestVolumeThreshold(2).failureRatio(1).build())
        CountDownLatch slowEntered = new CountDownLatch(1)
        CountDownLatch slowRelease = new CountDownLatch(1)
        Thread slow = Thread.start { operations.execute { slowEntered.countDown(); slowRelease.await(5, TimeUnit.SECONDS); "slow" } }
        slowEntered.await(5, TimeUnit.SECONDS)

        when:
        2.times {
            try {
                operations.execute { throw new IllegalStateException("down") }
            } catch (IllegalStateException ignored) {
            }
        }

        then:
        operations.currentState() == CircuitState.OPEN

        when:
        Thread.sleep(300)
        CountDownLatch trialEntered = new CountDownLatch(1)
        CountDownLatch trialRelease = new CountDownLatch(1)
        Thread trial = Thread.start { operations.execute { trialEntered.countDown(); trialRelease.await(5, TimeUnit.SECONDS); "trial" } }
        trialEntered.await(5, TimeUnit.SECONDS)
        slowRelease.countDown()
        slow.join(5000)

        then:
        registry.findSnapshot("stale-operations").get().state() == CircuitState.HALF_OPEN

        when:
        trialRelease.countDown()
        trial.join(5000)

        then:
        operations.currentState() == CircuitState.CLOSED
    }

    void "a publisher takes its permit when it is subscribed, not when it is created"() {
        given:
        2.times {
            try {
                Mono.from(service.lazy(true)).block()
            } catch (IllegalStateException ignored) {
            }
        }

        expect:
        registry.findState("lazy").get() == CircuitState.OPEN

        when: "the circuit half-opens, and a publisher is created but never subscribed"
        Thread.sleep(300)
        service.lazy(false)

        then: "the trial permit is still free"
        Mono.from(service.lazy(false)).block() == "ok"
        registry.findState("lazy").get() == CircuitState.CLOSED
    }

    void "a publisher cancelled before a value counts for nothing and returns its trial permit"() {
        when: "two calls of a closed circuit time out"
        2.times {
            Mono.from(service.hanging(false)).timeout(Duration.ofMillis(50)).onErrorResume { Mono.empty() }.block()
        }

        then: "they are not successes of the window"
        registry.findSnapshot("hanging").get().calls() == 0

        when: "the circuit opens, half-opens, and its trial call times out"
        2.times {
            try {
                Mono.from(service.hanging(true)).block()
            } catch (IllegalStateException ignored) {
            }
        }
        Thread.sleep(300)
        Mono.from(service.hanging(false)).timeout(Duration.ofMillis(50)).onErrorResume { Mono.empty() }.block()

        then: "the circuit is still half-open, and the trial permit is free again"
        registry.findSnapshot("hanging").get().state() == CircuitState.HALF_OPEN
        registry.findSnapshot("hanging").get().trials() == 0
    }

    void "a publisher cancelled after a value counts as a success"() {
        when: "the subscriber takes the first value and cancels"
        Mono.from(service.streaming()).block() == "first"

        then:
        registry.findSnapshot("streaming").get().calls() == 1
        registry.findSnapshot("streaming").get().failures() == 0
    }

    void "a publisher that succeeds after a retry counts once"() {
        when:
        List<String> values = Flux.from(service.flaky()).collectList().block()

        then:
        values == ["ok"]
        service.flakyCalls.get() == 2
        registry.findSnapshot("flaky").get().calls() == 1
    }

    void "a call interrupted while it waits for a retry returns its trial permit"() {
        given:
        CircuitBreakerPolicy.Builder window = CircuitBreakerPolicy.builder()
            .resetTimeout(Duration.ofMillis(500)).requestVolumeThreshold(1).failureRatio(1)
        CircuitBreakerOperations fast = registry.circuitBreaker("interrupted", window.maxAttempts(1).delay(Duration.ofMillis(1)).build())
        CircuitBreakerOperations slow = registry.circuitBreaker("interrupted", CircuitBreakerPolicy.builder()
            .resetTimeout(Duration.ofMillis(500)).requestVolumeThreshold(1).failureRatio(1)
            .maxAttempts(3).delay(Duration.ofSeconds(10)).build())

        when: "a failure opens the circuit, and it half-opens"
        try {
            fast.execute { throw new IllegalStateException("down") }
        } catch (IllegalStateException ignored) {
        }
        conditions.eventually {
            assert registry.findState("interrupted").get() == CircuitState.HALF_OPEN
        }

        and: "the trial call fails, waits for its retry and is interrupted"
        CountDownLatch attempted = new CountDownLatch(1)
        Thread trial = Thread.start {
            try {
                slow.execute { attempted.countDown(); throw new IllegalStateException("still down") }
            } catch (IllegalStateException ignored) {
            }
        }
        attempted.await(5, TimeUnit.SECONDS)
        Thread.sleep(50)
        trial.interrupt()
        trial.join(5000)

        then: "the trial permit can be taken again"
        fast.execute { "ok" } == "ok"
        registry.findState("interrupted").get() == CircuitState.CLOSED
    }

    void "a released guard permit returns its trial permit, and an abandoned one is given again after the reset timeout"() {
        given:
        CircuitBreakerGuard guard = registry.guard("guarded")
        guard.acquire().onFailure(new IllegalStateException("down"))
        conditions.eventually {
            assert guard.state == CircuitState.HALF_OPEN
        }

        when: "a trial permit is released without an outcome"
        CircuitBreakerGuard.Permit permit = guard.acquire()
        permit.release()
        permit.onFailure(new IllegalStateException("ignored after the release"))

        then: "the trial permit can be taken again"
        guard.state == CircuitState.HALF_OPEN
        registry.findSnapshot("guarded").get().trials() == 0

        when: "the next trial permit is abandoned"
        guard.acquire()
        guard.acquire()

        then:
        thrown(CircuitOpenException)

        and: "it is given again once the reset timeout elapses"
        conditions.eventually {
            CircuitBreakerGuard.Permit again = guard.acquire()
            again.onSuccess()
        }
        guard.state == CircuitState.CLOSED
    }

    void "an exception the retries exclude counts as a success of the window"() {
        when:
        service.excluded()

        then:
        thrown(UnsupportedOperationException)
        registry.findSnapshot("excluded").get().state() == CircuitState.CLOSED
        registry.findSnapshot("excluded").get().calls() == 1
        registry.findSnapshot("excluded").get().failures() == 0
    }

    void "a user that disagrees with the circuit of a name fails, whichever came first: #description"() {
        when:
        first.call(this)
        second.call(this)

        then:
        IllegalStateException e = thrown()
        e.message.contains('CircuitBreakerRegistry.guard("ordered")')
        e.message.contains('@CircuitBreaker of ' + CallService.name + '#ordered')
        e.message.contains('micronaut.retry.circuit-breakers.ordered')

        where:
        description             | first                                          | second
        "the guard first"       | { WindowedCircuitBreakerCallSpec s -> s.registry.guard("ordered") } | { WindowedCircuitBreakerCallSpec s -> s.service.ordered() }
        "the annotation first"  | { WindowedCircuitBreakerCallSpec s -> s.orderedFailure() }          | { WindowedCircuitBreakerCallSpec s -> s.registry.guard("ordered") }
    }

    private void orderedFailure() {
        try {
            service.ordered()
        } catch (UnsupportedOperationException ignored) {
        }
    }

    void "the users of a configured name take its window, and one that declares another reset fails"() {
        when: "an annotation without window settings of a configured name"
        2.times {
            try {
                service.configured()
            } catch (IllegalStateException ignored) {
            }
        }

        then: "it takes the configured window of two"
        registry.findSnapshot("configured").get().requestVolumeThreshold() == 2
        registry.findState("configured").get() == CircuitState.OPEN
        registry.guard("configured").state == CircuitState.OPEN

        when:
        service.configuredReset()

        then:
        IllegalStateException e = thrown()
        e.message.contains('micronaut.retry.circuit-breakers.configured-reset')
        e.message.contains('PT5S')
    }

    void "a named circuit breaker retries a checked exception and opens on it"() {
        given:
        AtomicInteger calls = new AtomicInteger()
        CircuitBreakerOperations operations = registry.circuitBreaker("checked")

        when:
        operations.executeCompletionStage { calls.incrementAndGet(); CompletableFuture.failedFuture(new IOException("io down")) }.toCompletableFuture().get()

        then:
        ExecutionException e = thrown()
        e.cause instanceof IOException
        calls.get() == 2
        operations.currentState() == CircuitState.OPEN
    }

    void "a guard and programmatic operations publish the events of the circuit"() {
        given:
        CircuitEvents events = context.getBean(CircuitEvents)

        when: "a guard opens a circuit without a window"
        registry.guard("events").acquire().onFailure(new IllegalStateException("down"))

        then:
        events.opened*.source*.methodName == ["events"]

        when: "a guard opens a circuit with a window"
        registry.guard("events-window").acquire().onFailure(new IllegalStateException("down"))

        then:
        events.opened*.source*.methodName == ["events", "events-window"]
        registry.findSnapshot("events-window").get().since() != null
    }

    @Singleton
    @Requires(property = "spec.name", value = "WindowedCircuitBreakerCallSpec")
    static class CircuitEvents {
        final List<CircuitOpenEvent> opened = new CopyOnWriteArrayList<>()
        final List<CircuitClosedEvent> closed = new CopyOnWriteArrayList<>()

        @io.micronaut.runtime.event.annotation.EventListener
        void onOpen(CircuitOpenEvent event) {
            opened.add(event)
        }

        @io.micronaut.runtime.event.annotation.EventListener
        void onClose(CircuitClosedEvent event) {
            closed.add(event)
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "WindowedCircuitBreakerCallSpec")
    static class CallService {
        final AtomicInteger flakyCalls = new AtomicInteger()

        @CircuitBreaker(name = "stale", attempts = "1", delay = "1ms", reset = "200ms", requestVolumeThreshold = "2", failureRatio = "1")
        String stale(CountDownLatch entered, CountDownLatch release, boolean fail) {
            entered?.countDown()
            release?.await(5, TimeUnit.SECONDS)
            if (fail) {
                throw new IllegalStateException("down")
            }
            return "ok"
        }

        @CircuitBreaker(name = "lazy", attempts = "1", delay = "1ms", reset = "200ms", requestVolumeThreshold = "2", failureRatio = "1")
        Publisher<String> lazy(boolean fail) {
            return fail ? Flux.error(new IllegalStateException("down")) : Flux.just("ok")
        }

        @CircuitBreaker(name = "hanging", attempts = "1", delay = "1ms", reset = "200ms", requestVolumeThreshold = "2", failureRatio = "1")
        Publisher<String> hanging(boolean fail) {
            return fail ? Flux.error(new IllegalStateException("down")) : Flux.never()
        }

        @CircuitBreaker(name = "streaming", attempts = "1", delay = "1ms", reset = "1m", requestVolumeThreshold = "2", failureRatio = "1")
        Publisher<String> streaming() {
            return Flux.just("first").concatWith(Flux.never())
        }

        @CircuitBreaker(name = "flaky", attempts = "2", delay = "1ms", reset = "1m", requestVolumeThreshold = "5", failureRatio = "1")
        Publisher<String> flaky() {
            return Flux.defer {
                flakyCalls.incrementAndGet() == 1 ? Flux.error(new IllegalStateException("once")) : Flux.just("ok")
            }
        }

        @CircuitBreaker(name = "excluded", attempts = "1", delay = "1ms", reset = "1m", excludes = UnsupportedOperationException, requestVolumeThreshold = "1", failureRatio = "1")
        String excluded() {
            throw new UnsupportedOperationException("not found")
        }

        @CircuitBreaker(name = "ordered", attempts = "1", delay = "1ms", reset = "1m", requestVolumeThreshold = "3", failureRatio = "1")
        String ordered() {
            throw new UnsupportedOperationException("down")
        }

        @CircuitBreaker(name = "configured", attempts = "1", delay = "1ms")
        String configured() {
            throw new IllegalStateException("down")
        }

        @CircuitBreaker(name = "configured-reset", attempts = "1", delay = "1ms", reset = "5s")
        String configuredReset() {
            return "ok"
        }
    }
}
