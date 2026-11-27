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
package io.micronaut.retry.intercept

import io.micronaut.context.ApplicationContext
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.retry.BulkheadRegistry
import io.micronaut.retry.annotation.Bulkhead
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.exception.BulkheadFullException
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BulkheadSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run('bulkhead.configured': '1')
    PollingConditions conditions = new PollingConditions(timeout: 5)

    private static Throwable failure(CompletableFuture<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS)
            return null
        } catch (ExecutionException e) {
            return e.cause
        }
    }

    void "calls beyond the limit fail at once, and a call that ends gives its place back"() {
        given:
        Calls calls = context.getBean(Calls)

        when: "two calls are running"
        CompletableFuture<String> first = calls.twoAtOnce()
        CompletableFuture<String> second = calls.twoAtOnce()

        then:
        calls.twoAtOnce.size() == 2
        !first.done

        when: "a third comes"
        CompletableFuture<String> third = calls.twoAtOnce()

        then: "it fails without the method being called"
        failure(third) instanceof BulkheadFullException
        failure(third).message.contains("is full: 2 calls are running")
        calls.twoAtOnce.size() == 2

        when: "the first ends"
        calls.twoAtOnce[0].complete("one")
        CompletableFuture<String> fourth = calls.twoAtOnce()

        then: "there is a place again"
        first.get() == "one"
        calls.twoAtOnce.size() == 3

        when: "a call fails"
        calls.twoAtOnce[1].completeExceptionally(new IllegalStateException("bad"))

        then: "its place is free too"
        failure(second) instanceof IllegalStateException
        calls.twoAtOnce().with { calls.twoAtOnce.size() == 4 }

        cleanup:
        calls.twoAtOnce.each { it.complete("end") }
    }

    void "a call waits for a place without a thread, and is called when another leaves"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        CompletableFuture<String> first = calls.waiting()
        CompletableFuture<String> second = calls.waiting()

        then: "the second has not reached the method"
        calls.waiting.size() == 1
        !second.done

        when:
        calls.waiting[0].complete("one")

        then: "it does when the first has left"
        first.get() == "one"
        calls.waiting.size() == 2

        when:
        calls.waiting[1].complete("two")

        then:
        second.get(5, TimeUnit.SECONDS) == "two"
    }

    void "a call that waited is called with the propagated context of its caller, in either mode of propagation"() {
        given:
        ApplicationContext scoped = ApplicationContext.run('micronaut.propagation': mode)
        Calls calls = scoped.getBean(Calls)

        when: "the second call is made with a context of its own, and the first ends on another thread"
        CompletableFuture<String> first = calls.waiting()
        CompletableFuture<String> second = PropagatedContext.getOrEmpty().plus(new Caller("second")).propagate({ calls.waiting() } as java.util.function.Supplier)
        CompletableFuture.runAsync { calls.waiting[0].complete("one") }.get(5, TimeUnit.SECONDS)

        then:
        first.get(5, TimeUnit.SECONDS) == "one"
        calls.waiting.size() == 2
        calls.callers == [null, "second"]

        cleanup:
        calls.waiting.each { it.complete("end") }
        scoped.close()

        where:
        mode << ["thread-local", "scoped-value"]
    }

    void "a call that waited too long fails, and the place it waited for is not lost"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        CompletableFuture<String> first = calls.shortWait()
        CompletableFuture<String> second = calls.shortWait()

        then:
        failure(second) instanceof BulkheadFullException
        calls.shortWait.size() == 1

        when:
        calls.shortWait[0].complete("one")
        CompletableFuture<String> third = calls.shortWait()

        then:
        first.get() == "one"
        calls.shortWait.size() == 2

        cleanup:
        calls.shortWait.each { it.complete("end") }
    }

    void "a method that answers on its thread holds its place while it runs"() {
        given:
        Calls calls = context.getBean(Calls)
        CountDownLatch entered = new CountDownLatch(1)
        CountDownLatch leave = new CountDownLatch(1)
        CompletableFuture<String> running = CompletableFuture.supplyAsync { calls.blocking(entered, leave) }
        entered.await(5, TimeUnit.SECONDS)

        when:
        calls.blocking(new CountDownLatch(1), new CountDownLatch(0))

        then:
        thrown(BulkheadFullException)

        when:
        leave.countDown()

        then:
        running.get(5, TimeUnit.SECONDS) == "done"
        calls.blocking(new CountDownLatch(1), new CountDownLatch(0)) == "done"
    }

    void "a method that answers on its thread waits on it"() {
        given:
        Calls calls = context.getBean(Calls)
        CountDownLatch entered = new CountDownLatch(1)
        CountDownLatch leave = new CountDownLatch(1)
        CompletableFuture<String> running = CompletableFuture.supplyAsync { calls.blockingWaits(entered, leave) }
        entered.await(5, TimeUnit.SECONDS)

        when:
        CompletableFuture<String> waits = CompletableFuture.supplyAsync { calls.blockingWaits(new CountDownLatch(1), new CountDownLatch(0)) }
        Thread.sleep(100)

        then:
        !waits.done

        when:
        leave.countDown()

        then:
        running.get(5, TimeUnit.SECONDS) == "done"
        waits.get(5, TimeUnit.SECONDS) == "done"
    }

    void "a publisher holds its place from its subscription to its end or its cancellation"() {
        given:
        Calls calls = context.getBean(Calls)

        when: "a publisher nobody subscribed to"
        Publisher<String> unused = calls.reactive()

        then: "takes no place"
        unused != null
        calls.reactive.get() == 0

        when:
        def first = Mono.from(calls.reactive()).subscribe()

        then:
        conditions.eventually { calls.reactive.get() == 1 }

        when:
        Mono.from(calls.reactive()).block()

        then:
        thrown(BulkheadFullException)
        calls.reactive.get() == 1

        when: "the subscriber cancels"
        first.dispose()
        def third = Mono.from(calls.reactive()).subscribe()

        then:
        conditions.eventually { calls.reactive.get() == 2 }

        cleanup:
        third?.dispose()
    }

    void "a subscriber that cancels while it waits is not handed a place"() {
        given:
        Calls calls = context.getBean(Calls)
        BulkheadRegistry registry = context.getBean(BulkheadRegistry)

        when:
        def first = Mono.from(calls.reactiveWaits()).subscribe()
        def second = Mono.from(calls.reactiveWaits()).subscribe()

        then:
        conditions.eventually {
            registry.findSnapshot("reactive-waits").get().active() == 1
            registry.findSnapshot("reactive-waits").get().waiting() == 1
        }

        when:
        second.dispose()
        first.dispose()

        then:
        conditions.eventually {
            registry.findSnapshot("reactive-waits").get().active() == 0
            registry.findSnapshot("reactive-waits").get().waiting() == 0
        }
        calls.reactiveWaits.get() == 1
    }

    void "methods of one name share the places, and the registry says how full it is"() {
        given:
        Calls calls = context.getBean(Calls)
        BulkheadRegistry registry = context.getBean(BulkheadRegistry)

        when:
        CompletableFuture<String> one = calls.sharedOne()
        CompletableFuture<String> two = calls.sharedTwo()

        then:
        failure(two) instanceof BulkheadFullException
        failure(two).message.contains("[shared]")
        registry.names.contains("shared")
        with(registry.findSnapshot("shared").get()) {
            maxConcurrent() == 1
            active() == 1
            waiting() == 0
        }

        when:
        calls.shared[0].complete("one")

        then:
        one.get() == "one"
        registry.findSnapshot("shared").get().active() == 0
        registry.findSnapshot("unknown").empty
    }

    void "methods of one name that declare different limits fail"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        calls.disagreeOne().complete("x")
        CompletableFuture<String> two = calls.disagreeTwo()

        then:
        failure(two) instanceof IllegalStateException
        failure(two).message.contains("The bulkhead [disagree] runs 1 calls at the same time")
        failure(two).message.contains("disagreeTwo declares 2")
    }

    void "the limit is read from the configuration"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        CompletableFuture<String> first = calls.configured()
        CompletableFuture<String> second = calls.configured()

        then:
        !first.done
        failure(second) instanceof BulkheadFullException
    }

    void "each attempt of a retry asks for a place"() {
        given:
        Calls calls = context.getBean(Calls)

        when: "a call holds the one place and leaves while another retries"
        CompletableFuture<String> first = calls.retried()
        CompletableFuture<String> second = calls.retried()
        Thread.sleep(50)
        calls.retried[0].complete("one")

        then:
        first.get() == "one"
        conditions.eventually { calls.retried.size() == 2 }

        when:
        calls.retried[1].complete("two")

        then:
        second.get(5, TimeUnit.SECONDS) == "two"
    }

    static record Caller(String name) implements PropagatedContextElement {
    }

    @Singleton
    static class Calls {
        final List<CompletableFuture<String>> twoAtOnce = new CopyOnWriteArrayList<>()
        final List<CompletableFuture<String>> waiting = new CopyOnWriteArrayList<>()
        final List<CompletableFuture<String>> shortWait = new CopyOnWriteArrayList<>()
        final List<CompletableFuture<String>> shared = new CopyOnWriteArrayList<>()
        final List<CompletableFuture<String>> retried = new CopyOnWriteArrayList<>()
        final List<String> callers = Collections.synchronizedList(new ArrayList<String>())
        final AtomicInteger reactive = new AtomicInteger()
        final AtomicInteger reactiveWaits = new AtomicInteger()

        private static CompletableFuture<String> pending(List<CompletableFuture<String>> calls) {
            CompletableFuture<String> future = new CompletableFuture<>()
            calls.add(future)
            return future
        }

        @Bulkhead(maxConcurrent = "2")
        CompletableFuture<String> twoAtOnce() {
            pending(twoAtOnce)
        }

        @Bulkhead(maxConcurrent = "1", maxWait = "10s")
        CompletableFuture<String> waiting() {
            callers.add(PropagatedContext.getOrEmpty().find(Caller).map { it.name() }.orElse(null))
            pending(waiting)
        }

        @Bulkhead(maxConcurrent = "1", maxWait = "100ms")
        CompletableFuture<String> shortWait() {
            pending(shortWait)
        }

        @Bulkhead(maxConcurrent = "1")
        String blocking(CountDownLatch entered, CountDownLatch leave) {
            entered.countDown()
            leave.await(5, TimeUnit.SECONDS)
            "done"
        }

        @Bulkhead(maxConcurrent = "1", maxWait = "10s")
        String blockingWaits(CountDownLatch entered, CountDownLatch leave) {
            entered.countDown()
            leave.await(5, TimeUnit.SECONDS)
            "done"
        }

        @Bulkhead(maxConcurrent = "1")
        Publisher<String> reactive() {
            Mono.<String> never().doOnSubscribe { reactive.incrementAndGet() }
        }

        @Bulkhead(name = "reactive-waits", maxConcurrent = "1", maxWait = "10s")
        Publisher<String> reactiveWaits() {
            Mono.<String> never().doOnSubscribe { reactiveWaits.incrementAndGet() }
        }

        @Bulkhead(name = "shared", maxConcurrent = "1")
        CompletableFuture<String> sharedOne() {
            pending(shared)
        }

        @Bulkhead(name = "shared", maxConcurrent = "1")
        CompletableFuture<String> sharedTwo() {
            pending(shared)
        }

        @Bulkhead(name = "disagree", maxConcurrent = "1")
        CompletableFuture<String> disagreeOne() {
            new CompletableFuture<String>()
        }

        @Bulkhead(name = "disagree", maxConcurrent = "2")
        CompletableFuture<String> disagreeTwo() {
            new CompletableFuture<String>()
        }

        @Bulkhead(maxConcurrent = '${bulkhead.configured}')
        CompletableFuture<String> configured() {
            new CompletableFuture<String>()
        }

        @Retryable(attempts = "20", delay = "20ms")
        @Bulkhead(maxConcurrent = "1")
        CompletableFuture<String> retried() {
            pending(retried)
        }
    }
}
