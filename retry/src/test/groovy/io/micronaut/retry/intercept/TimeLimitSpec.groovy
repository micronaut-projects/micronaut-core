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
import io.micronaut.retry.annotation.Bulkhead
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.retry.annotation.Retryable
import io.micronaut.retry.annotation.TimeLimit
import io.micronaut.retry.exception.TimeLimitExceededException
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TimeLimitSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run('limit.configured': '100ms')

    private static Throwable failure(CompletableFuture<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS)
            return null
        } catch (ExecutionException e) {
            return e.cause
        }
    }

    void "a result that is there in time is the result"() {
        given:
        Calls calls = context.getBean(Calls)

        expect:
        calls.inTime().get(5, TimeUnit.SECONDS) == "in time"
        Mono.from(calls.reactiveInTime()).block() == "in time"
    }

    void "a stage that is not complete at the limit fails, and a future is cancelled"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        long start = System.nanoTime()
        CompletableFuture<String> result = calls.never()
        Throwable failure = failure(result)
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        then:
        failure instanceof TimeLimitExceededException
        failure.message.endsWith('Calls#never took longer than 100ms')
        millis >= 90
        millis < 3000
        calls.never[0].cancelled
    }

    void "the time a method takes to return its stage or its publisher counts"() {
        given:
        Calls calls = context.getBean(Calls)

        when: "the method takes longer than its limit to return a stage that is complete"
        Throwable late = failure(calls.returnsLate())

        then:
        late instanceof TimeLimitExceededException

        when:
        Mono.from(calls.returnsLatePublisher()).block()

        then:
        thrown(TimeLimitExceededException)
    }

    void "a failure of the method is the failure"() {
        given:
        Calls calls = context.getBean(Calls)

        expect:
        failure(calls.fails()) instanceof IllegalArgumentException
    }

    void "a publisher that has not ended at the limit fails and is cancelled"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        List<String> seen = new CopyOnWriteArrayList<>()
        Flux.from(calls.stalls()).doOnNext { seen.add(it) }.blockLast()

        then:
        thrown(TimeLimitExceededException)
        seen == ["one", "two"]
        calls.cancelled.get()
    }

    void "a method that answers on its thread is refused"() {
        given:
        Calls calls = context.getBean(Calls)

        when:
        calls.onItsThread()

        then:
        IllegalStateException e = thrown()
        e.message.contains("answers on the thread it is called on")
    }

    void "a limit that is no time is refused"() {
        given:
        Calls calls = context.getBean(Calls)

        expect:
        failure(calls.noTime()).message.contains("[soon] is not a time")
    }

    void "each attempt of a retry has the limit to itself"() {
        given:
        Calls calls = context.getBean(Calls)

        expect: "two attempts run out of time and the third answers"
        calls.retried().get(5, TimeUnit.SECONDS) == "attempt 3"
    }

    void "a fallback answers for a call that ran out of time"() {
        given:
        Slow slow = context.getBean(Slow)

        expect:
        slow.answer().get(5, TimeUnit.SECONDS) == "fallback"
    }

    void "the wait for a place of a bulkhead counts, and a call that ran out of time no longer waits for one"() {
        given:
        Calls calls = context.getBean(Calls)

        when: "a call holds the one place and a second waits for it"
        CompletableFuture<String> first = calls.limitedPlace()
        CompletableFuture<String> second = calls.limitedPlace()

        then: "both run out of time"
        failure(first) instanceof TimeLimitExceededException
        failure(second) instanceof TimeLimitExceededException

        when: "the work of the first ends"
        calls.limitedPlace[0].complete("late")

        then: "the second, which nobody waits for, is not called"
        Thread.sleep(100)
        calls.limitedPlace.size() == 1

        and: "the place is free"
        calls.limitedPlace().with { calls.limitedPlace.size() == 2 }
    }

    @Singleton
    static class Calls {
        final List<CompletableFuture<String>> never = new CopyOnWriteArrayList<>()
        final List<CompletableFuture<String>> limitedPlace = new CopyOnWriteArrayList<>()
        final AtomicBoolean cancelled = new AtomicBoolean()
        final AtomicInteger attempts = new AtomicInteger()

        @TimeLimit("5s")
        CompletableFuture<String> inTime() {
            CompletableFuture.completedFuture("in time")
        }

        @TimeLimit("5s")
        Publisher<String> reactiveInTime() {
            Mono.just("in time")
        }

        @TimeLimit('${limit.configured}')
        CompletableFuture<String> never() {
            CompletableFuture<String> future = new CompletableFuture<>()
            never.add(future)
            future
        }

        @TimeLimit("100ms")
        CompletableFuture<String> returnsLate() {
            Thread.sleep(400)
            CompletableFuture.completedFuture("late")
        }

        @TimeLimit("100ms")
        Publisher<String> returnsLatePublisher() {
            Thread.sleep(400)
            Mono.just("late")
        }

        @TimeLimit("5s")
        CompletableFuture<String> fails() {
            CompletableFuture.failedFuture(new IllegalArgumentException("bad"))
        }

        @TimeLimit("200ms")
        Publisher<String> stalls() {
            Flux.concat(Flux.just("one", "two"), Flux.<String> never()).doOnCancel { cancelled.set(true) }
        }

        @TimeLimit("1s")
        String onItsThread() {
            "never"
        }

        @TimeLimit("soon")
        CompletableFuture<String> noTime() {
            CompletableFuture.completedFuture("never")
        }

        @Retryable(attempts = "3", delay = "10ms")
        @TimeLimit("100ms")
        CompletableFuture<String> retried() {
            int attempt = attempts.incrementAndGet()
            attempt < 3 ? new CompletableFuture<String>() : CompletableFuture.completedFuture("attempt " + attempt)
        }

        @TimeLimit("150ms")
        @Bulkhead(maxConcurrent = "1", maxWait = "10s")
        CompletableFuture<String> limitedPlace() {
            // not the future that is returned, so that the limit does not cancel the work
            CompletableFuture<String> work = new CompletableFuture<>()
            limitedPlace.add(work)
            work.thenApply { it }
        }
    }

    interface Answers {
        CompletableFuture<String> answer()
    }

    @Singleton
    @Recoverable(api = Answers)
    static class Slow implements Answers {
        @Override
        @TimeLimit("100ms")
        CompletableFuture<String> answer() {
            new CompletableFuture<String>()
        }
    }

    @Singleton
    @Fallback
    static class Quick implements Answers {
        @Override
        CompletableFuture<String> answer() {
            CompletableFuture.completedFuture("fallback")
        }
    }
}
