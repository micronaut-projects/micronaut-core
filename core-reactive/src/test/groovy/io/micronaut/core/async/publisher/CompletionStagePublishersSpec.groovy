package io.micronaut.core.async.publisher

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class CompletionStagePublishersSpec extends Specification {

    void 'first completes with the first item and cancels the rest'() {
        given:
        def cancelled = new AtomicInteger()
        def requested = new AtomicLong()
        def publisher = Flux.range(1, 10).doOnRequest(n -> requested.addAndGet(n)).doOnCancel(() -> cancelled.incrementAndGet())

        when:
        def future = CompletionStagePublishers.first(publisher, null)

        then:
        future.getNow(null) == 1
        requested.get() == 1
        cancelled.get() == 1
    }

    void 'first completes with the empty value when the publisher is empty'() {
        expect:
        CompletionStagePublishers.first(Publishers.empty(), 'none').getNow('pending') == 'none'
        CompletionStagePublishers.first(Publishers.empty(), null).isDone()
        CompletionStagePublishers.first(Publishers.empty(), null).getNow('pending') == null
    }

    void 'first fails with the error of the publisher'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        def future = CompletionStagePublishers.first(Publishers.just(error), null)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    void 'first fails when the publisher throws on subscribe'() {
        given:
        def error = new IllegalStateException('boom')
        def publisher = { s -> throw error } as org.reactivestreams.Publisher<String>

        when:
        def future = CompletionStagePublishers.first(publisher, null)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    void 'cancelling the future of first cancels the subscription'() {
        given:
        def cancelled = new AtomicInteger()
        def future = CompletionStagePublishers.first(Flux.never().doOnCancel(() -> cancelled.incrementAndGet()), null)

        expect:
        !future.isDone()
        cancelled.get() == 0

        when:
        future.cancel(false)

        then:
        future.isCancelled()
        cancelled.get() == 1
    }

    void 'first completes once the publisher emits later'() {
        given:
        def source = new CompletableFuture<String>()
        def future = CompletionStagePublishers.first(Mono.fromFuture(source), null)

        expect:
        !future.isDone()

        when:
        source.complete('later')

        then:
        future.getNow(null) == 'later'
    }

    void 'collect completes with all the items'() {
        expect:
        CompletionStagePublishers.collect(Flux.just(1, 2, 3)).getNow(null) == [1, 2, 3]
        CompletionStagePublishers.collect(Publishers.empty()).getNow(null) == []
    }

    void 'collect fails with the error of the publisher'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        CompletionStagePublishers.collect(Flux.just(1).concatWith(Mono.error(error))).get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    void 'cancelling the future of collect cancels the subscription'() {
        given:
        def cancelled = new AtomicInteger()
        def future = CompletionStagePublishers.collect(Flux.just(1).concatWith(Flux.never()).doOnCancel(() -> cancelled.incrementAndGet()))

        when:
        future.cancel(false)

        then:
        cancelled.get() == 1
    }

    void 'concat concatenates the lists in the order of the stages'() {
        given:
        def first = new CompletableFuture<List<String>>()
        def second = CompletableFuture.completedFuture(['c'])
        def third = CompletableFuture.completedFuture(null)

        when:
        def result = CompletionStagePublishers.concat([first, second, third])

        then:
        !result.isDone()

        when:
        first.complete(['a', 'b'])

        then:
        result.getNow(null) == ['a', 'b', 'c']
    }

    void 'concat of no stage is an empty list'() {
        expect:
        CompletionStagePublishers.concat([]).getNow(null) == []
    }

    void 'the first stage that fails fails concat and cancels the others'() {
        given:
        def pending = new CompletableFuture<List<String>>()
        def failing = new CompletableFuture<List<String>>()
        def done = CompletableFuture.completedFuture(['a'])
        def error = new IllegalStateException('boom')

        when:
        def result = CompletionStagePublishers.concat([done, pending, failing])
        failing.completeExceptionally(new CompletionException(error))
        result.get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
        pending.isCancelled()
    }

    void 'cancelling concat cancels the stages'() {
        given:
        def first = new CompletableFuture<List<String>>()
        def second = new CompletableFuture<List<String>>()

        when:
        def result = CompletionStagePublishers.concat([first, second])
        result.cancel(false)

        then:
        first.isCancelled()
        second.isCancelled()
    }

    void 'cancelling a derived future cancels its source'() {
        given:
        def source = new CompletableFuture<String>()
        def derived = CompletionStagePublishers.cancelling(source, source.thenApply(String::length))

        when:
        derived.cancel(false)

        then:
        source.isCancelled()
    }

    void 'unwrap removes the completion exception'() {
        given:
        def error = new IllegalStateException('boom')

        expect:
        CompletionStagePublishers.unwrap(new CompletionException(error)).is(error)
        CompletionStagePublishers.unwrap(error).is(error)
    }
}
