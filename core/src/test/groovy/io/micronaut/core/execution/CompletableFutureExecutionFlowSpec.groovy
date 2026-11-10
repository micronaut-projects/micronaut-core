package io.micronaut.core.execution

import spock.lang.Specification

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class CompletableFutureExecutionFlowSpec extends Specification {

    def "a pending future does not complete"() {
        expect:
        CompletableFutureExecutionFlow.just(new CompletableFuture<String>()).tryComplete() == null
    }

    def "a completed future gives its value"() {
        when:
        def flow = CompletableFutureExecutionFlow.just(CompletableFuture.completedFuture("value"))

        then:
        flow.tryCompleteValue() == "value"
        flow.tryCompleteError() == null
        flow.map { it + "-mapped" }.tryCompleteValue() == "value-mapped"
    }

    def "a future completed with null gives a completed flow with a null value"() {
        when:
        def imperative = CompletableFutureExecutionFlow.just(CompletableFuture.completedFuture(null)).tryComplete()

        then:
        imperative != null
        imperative.value == null
        imperative.error == null
    }

    def "a failed future gives its exception"() {
        given:
        def failure = new IllegalStateException("failed")
        def future = new CompletableFuture<String>()
        future.completeExceptionally(failure)

        when:
        def flow = CompletableFutureExecutionFlow.just(future)

        then:
        flow.tryCompleteError().is(failure)
        flow.tryCompleteValue() == null
        flow.map { throw new AssertionError("should not be called") }.tryCompleteError().is(failure)
        flow.onErrorResume { ExecutionFlow.just(it.message) }.tryCompleteValue() == "failed"
    }

    def "a future failed with a CompletionException gives the cause"() {
        given:
        def failure = new IllegalStateException("failed")
        def future = new CompletableFuture<String>()
        future.completeExceptionally(new CompletionException(failure))

        expect:
        CompletableFutureExecutionFlow.just(future).tryCompleteError().is(failure)
    }

    def "a future failed with a CompletionException without a cause gives that exception"() {
        given:
        def failure = new CompletionException("no cause", null)
        def future = new CompletableFuture<String>()
        future.completeExceptionally(failure)

        when:
        def imperative = CompletableFutureExecutionFlow.just(future).tryComplete()

        then:
        imperative.error.is(failure)
        imperative.value == null
    }

    def "a dependent stage of a failed future gives the original exception"() {
        given:
        def failure = new IllegalStateException("failed")
        def future = new CompletableFuture<String>()
        def dependent = future.thenApply { it + "-applied" }
        future.completeExceptionally(failure)

        expect:
        CompletableFutureExecutionFlow.just(dependent).tryCompleteError().is(failure)
    }

    def "a future that fails later gives its exception to the operators"() {
        given:
        def failure = new IllegalStateException("failed")
        def future = new CompletableFuture<String>()
        def flow = CompletableFutureExecutionFlow.just(future)
            .map { throw new AssertionError("should not be called") }
        def resumed = new CompletableFuture<Object>()
        flow.onErrorResume { ExecutionFlow.just(it) }.completeTo(resumed)

        when:
        future.completeExceptionally(failure)

        then:
        resumed.getNow(null).is(failure)
    }

    def "a future that fails later with a CompletionException gives the cause"() {
        given:
        def failure = new IllegalStateException("failed")
        def future = new CompletableFuture<String>()
        Throwable completed = null
        def target = new CompletableFuture<Object>()
        def flow = CompletableFutureExecutionFlow.just(future)
        flow.onComplete { value, error -> completed = error }
        CompletableFutureExecutionFlow.just(future).completeTo(target)

        when:
        future.completeExceptionally(new CompletionException(failure))

        then:
        completed.is(failure)
        target.exceptionNow().is(failure)
    }

    def "a future that fails later with a CompletionException without a cause gives that exception"() {
        given:
        def failure = new CompletionException("no cause", null)
        def future = new CompletableFuture<String>()
        Throwable completed = null
        Object resumedWith = null
        def target = new CompletableFuture<Object>()
        CompletableFutureExecutionFlow.just(future).onComplete { value, error -> completed = error }
        CompletableFutureExecutionFlow.just(future).completeTo(target)
        CompletableFutureExecutionFlow.just(future).onErrorResume {
            resumedWith = it
            ExecutionFlow.just("resumed")
        }.onComplete { value, error -> }

        when:
        future.completeExceptionally(failure)

        then:
        completed.is(failure)
        target.exceptionNow().is(failure)
        resumedWith.is(failure)
    }

    def "a cancelled future gives a CancellationException"() {
        given:
        def future = new CompletableFuture<String>()
        future.cancel(false)

        when:
        def flow = CompletableFutureExecutionFlow.just(future)

        then:
        flow.tryCompleteError() instanceof CancellationException
        flow.tryCompleteValue() == null
        flow.map { throw new AssertionError("should not be called") }.tryCompleteError() instanceof CancellationException
    }

    def "a dependent stage of a cancelled future gives a CancellationException"() {
        given:
        def future = new CompletableFuture<String>()
        def dependent = future.thenApply { it + "-applied" }
        future.cancel(false)

        expect:
        CompletableFutureExecutionFlow.just(dependent).tryCompleteError() instanceof CancellationException
    }
}
