package io.micronaut.core.async.publisher

import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.time.Duration

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

    void 'the first stage that fails fails concat and cancels the futures of the class'() {
        given:
        def pending = CompletionStagePublishers.<List<String>>future()
        def foreign = new CompletableFuture<List<String>>()
        def failing = new CompletableFuture<List<String>>()
        def done = CompletableFuture.completedFuture(['a'])
        def error = new IllegalStateException('boom')

        when:
        def result = CompletionStagePublishers.concat([done, pending, foreign, failing])
        failing.completeExceptionally(new CompletionException(error))
        result.get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
        pending.isCancelled()
        !foreign.isDone()

        when: 'a late result is ignored'
        foreign.complete(['late'])

        then:
        result.isCompletedExceptionally()
    }

    void 'cancelling concat cancels the futures of the class only'() {
        given:
        def owned = CompletionStagePublishers.<List<String>>future()
        def foreign = new CompletableFuture<List<String>>()

        when:
        def result = CompletionStagePublishers.concat([owned, foreign])
        result.cancel(false)

        then:
        owned.isCancelled()
        !foreign.isDone()
    }

    void 'cancelling a derived future cancels its source when the source is a future of the class'() {
        given:
        def source = CompletionStagePublishers.<String>future()
        def foreign = new CompletableFuture<String>()
        def derived = CompletionStagePublishers.cancelling(source, source.thenApply(String::length))
        def derivedFromForeign = CompletionStagePublishers.cancelling(foreign, foreign.thenApply(String::length))

        when:
        derived.cancel(false)
        derivedFromForeign.cancel(false)

        then:
        source.isCancelled()
        !foreign.isDone()
    }

    void 'the futures derived from a future of the class are not owned'() {
        given:
        def source = CompletionStagePublishers.<String>future()
        def derived = source.thenApply(String::length)

        when:
        CompletionStagePublishers.cancel(derived)
        CompletionStagePublishers.cancel(new CompletableFuture<String>())

        then:
        !derived.isDone()
    }

    void 'map transforms the value, unwraps errors and cancels an owned source'() {
        given:
        def source = CompletionStagePublishers.<String>future()
        def failing = new CompletableFuture<String>()
        def error = new IllegalStateException('boom')

        when:
        def mapped = CompletionStagePublishers.map(source, String::length)
        def failed = CompletionStagePublishers.map(failing, String::length)
        def thrown = CompletionStagePublishers.map(CompletableFuture.completedFuture('a'), { throw error })
        failing.completeExceptionally(new CompletionException(error))
        source.complete('abc')

        then:
        mapped.getNow(null) == 3
        CompletionStagePublishers.unwrap(failed.handle { v, t -> t }.join()).is(error)
        thrown.handle { v, t -> t }.join().is(error)

        when:
        def other = CompletionStagePublishers.<String>future()
        CompletionStagePublishers.map(other, String::length).cancel(false)

        then:
        other.isCancelled()
    }

    void 'compose completes with the next stage and cancels the owned stages'() {
        given:
        def source = CompletionStagePublishers.<String>future()
        def next = CompletionStagePublishers.<Integer>future()

        when:
        def composed = CompletionStagePublishers.compose(source, { next })
        source.complete('abc')

        then:
        !composed.isDone()

        when:
        composed.cancel(false)

        then:
        next.isCancelled()

        when:
        def done = CompletionStagePublishers.compose(CompletableFuture.completedFuture('abc'), { CompletableFuture.completedFuture(it.length()) })

        then:
        done.getNow(null) == 3
    }

    void 'orElse uses the fallback when the stage is null'() {
        given:
        def stage = CompletableFuture.completedFuture('value')

        expect:
        CompletionStagePublishers.orElse(stage, { throw new AssertionError() }).is(stage)
        CompletionStagePublishers.orElse(null, { CompletableFuture.completedFuture('fallback') }).toCompletableFuture().join() == 'fallback'
    }

    void 'orElseIfNull uses the fallback when the stage is null or completes with null'() {
        given:
        def stage = CompletableFuture.completedFuture('value')
        def pending = new CompletableFuture<String>()

        when:
        def later = CompletionStagePublishers.orElseIfNull(pending, { CompletableFuture.completedFuture('fallback') })

        then:
        CompletionStagePublishers.orElseIfNull(stage, { throw new AssertionError() }).is(stage)
        CompletionStagePublishers.orElseIfNull(null, { CompletableFuture.completedFuture('fallback') }).toCompletableFuture().join() == 'fallback'
        CompletionStagePublishers.orElseIfNull(CompletableFuture.completedFuture(null), { CompletableFuture.completedFuture('fallback') }).toCompletableFuture().join() == 'fallback'
        !later.toCompletableFuture().isDone()

        when:
        pending.complete(null)

        then:
        later.toCompletableFuture().join() == 'fallback'
    }

    void 'a publisher that is not a Reactor publisher is subscribed to without the Reactor context'() {
        given:
        def element = new TestElement('request')
        def subscriberType = new AtomicReference<Class>()
        def seen = new AtomicReference<String>()
        def publisher = { org.reactivestreams.Subscriber s ->
            subscriberType.set(s.getClass())
            seen.set(PropagatedContext.find().flatMap { it.find(TestElement) }.map { it.value }.orElse('none'))
            Publishers.just('value').subscribe(s)
        } as org.reactivestreams.Publisher<String>

        when:
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        CompletableFuture<String> future
        try {
            future = CompletionStagePublishers.first(publisher, null)
        } finally {
            scope.close()
        }

        then:
        future.getNow(null) == 'value'
        seen.get() == 'request'
        !reactor.core.CoreSubscriber.isAssignableFrom(subscriberType.get())
    }

    void 'unwrap removes the completion exception'() {
        given:
        def error = new IllegalStateException('boom')

        expect:
        CompletionStagePublishers.unwrap(new CompletionException(error)).is(error)
        CompletionStagePublishers.unwrap(error).is(error)
    }

    void 'toPublisher obtains the stage on request and emits its value'() {
        given:
        def calls = new AtomicInteger()
        def publisher = CompletionStagePublishers.toPublisher {
            calls.incrementAndGet()
            CompletableFuture.completedFuture('value')
        }

        expect:
        calls.get() == 0
        Flux.from(publisher).collectList().block() == ['value']
        calls.get() == 1
        Flux.from(publisher).collectList().block() == ['value']
        calls.get() == 2
    }

    void 'toPublisher completes empty for a null value'() {
        expect:
        Flux.from(CompletionStagePublishers.toPublisher { CompletableFuture.completedFuture(null) }).collectList().block() == []
    }

    void 'toPublisher fails with the unwrapped error of the stage'() {
        given:
        def error = new IllegalStateException('boom')
        def stage = CompletableFuture.supplyAsync { throw error }

        when:
        Mono.from(CompletionStagePublishers.toPublisher { stage }).block()

        then:
        def e = thrown(IllegalStateException)
        e.is(error)
    }

    void 'toPublisher fails when the supplier throws'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        Mono.from(CompletionStagePublishers.toPublisher { throw error }).block()

        then:
        def e = thrown(IllegalStateException)
        e.is(error)
    }

    void 'cancelling the toPublisher subscription cancels a stage of the class only'() {
        given:
        CompletableFuture<String> stage = CompletionStagePublishers.future()
        def shared = new CompletableFuture<String>()

        when:
        Mono.from(CompletionStagePublishers.toPublisher { stage }).subscribe().dispose()
        Mono.from(CompletionStagePublishers.toPublisher { shared }).subscribe().dispose()

        then:
        stage.cancelled
        !shared.done
    }

    void 'a non-positive request after the stage is obtained cancels it'() {
        given:
        CompletableFuture<String> stage = CompletionStagePublishers.future()
        def errors = []
        org.reactivestreams.Subscription subscription
        CompletionStagePublishers.toPublisher { stage }.subscribe(new org.reactivestreams.Subscriber<String>() {
            void onSubscribe(org.reactivestreams.Subscription s) { subscription = s }
            void onNext(String t) {}
            void onError(Throwable t) { errors << t }
            void onComplete() {}
        })

        when:
        subscription.request(1)
        subscription.request(0)

        then:
        stage.cancelled
        errors.size() == 1
        errors[0] instanceof IllegalArgumentException
    }

    void 'fromList emits the items as they are requested'() {
        given:
        def items = []
        def completed = false
        org.reactivestreams.Subscription subscription
        CompletionStagePublishers.fromList(['a', 'b', 'c']).subscribe(new org.reactivestreams.Subscriber<String>() {
            void onSubscribe(org.reactivestreams.Subscription s) { subscription = s }
            void onNext(String t) { items << t }
            void onError(Throwable t) {}
            void onComplete() { completed = true }
        })

        when:
        subscription.request(2)

        then:
        items == ['a', 'b']
        !completed

        when:
        subscription.request(1)

        then:
        items == ['a', 'b', 'c']
        completed
        Flux.from(CompletionStagePublishers.fromList([])).collectList().block() == []
        Flux.from(CompletionStagePublishers.fromList([1, 2, 3])).collectList().block() == [1, 2, 3]
    }

    void 'first subscribes with the propagated context in the Reactor context and as a thread-local'() {
        given:
        def element = new TestElement('request')
        def seenInSubscribe = new AtomicReference<String>()
        def publisher = Mono.deferContextual { view ->
            seenInSubscribe.set(PropagatedContext.find().flatMap { it.find(TestElement) }.map { it.value }.orElse('none'))
            Mono.just(ReactorPropagation.findContextElement(view, TestElement).map { it.value }.orElse('none'))
        }

        when:
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        CompletableFuture<String> future
        try {
            future = CompletionStagePublishers.first(publisher, null)
        } finally {
            scope.close()
        }

        then:
        future.getNow(null) == 'request'
        seenInSubscribe.get() == 'request'
    }

    void 'collect subscribes with the propagated context in the Reactor context'() {
        given:
        def element = new TestElement('request')
        def publisher = Flux.deferContextual { view ->
            Flux.just(ReactorPropagation.findContextElement(view, TestElement).map { it.value }.orElse('none'), 'second')
        }

        when:
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        CompletableFuture<List<String>> future
        try {
            future = CompletionStagePublishers.collect(publisher)
        } finally {
            scope.close()
        }

        then:
        future.getNow(null) == ['request', 'second']
    }

    void 'a publisher that completes on another thread completes the future in the propagated context'() {
        given:
        def element = new TestElement('request')
        def publisher = Mono.delay(Duration.ofMillis(10)).map { 'value' }

        when:
        def scope = PropagatedContext.getOrEmpty().plus(element).propagate()
        CompletableFuture<String> future
        try {
            future = CompletionStagePublishers.first(publisher, null)
        } finally {
            scope.close()
        }
        def seen = future.thenApply { PropagatedContext.find().flatMap { it.find(TestElement) }.map { it.value }.orElse('none') }

        then:
        seen.get() == 'request'
    }

    void 'without a propagated context the publisher is subscribed to as it is'() {
        given:
        def publisher = Mono.deferContextual { view ->
            Mono.just(ReactorPropagation.findPropagatedContext(view).isPresent())
        }

        expect:
        !PropagatedContext.exists()
        CompletionStagePublishers.first(publisher, null).getNow(null) == false
    }

    static class TestElement implements PropagatedContextElement {
        final String value

        TestElement(String value) {
            this.value = value
        }
    }

    void 'toPublisher obtains the stage once per subscription, whatever the requests'() {
        given:
        def calls = new AtomicInteger()
        def publisher = CompletionStagePublishers.toPublisher {
            calls.incrementAndGet()
            new CompletableFuture<String>()
        }
        def received = []
        org.reactivestreams.Subscription subscription

        when:
        publisher.subscribe(new org.reactivestreams.Subscriber<String>() {
            void onSubscribe(org.reactivestreams.Subscription s) { subscription = s }
            void onNext(String s) { received << s }
            void onError(Throwable t) { received << t }
            void onComplete() { received << 'complete' }
        })

        then:
        calls.get() == 0

        when:
        subscription.request(1)
        subscription.request(1)
        subscription.request(Long.MAX_VALUE)

        then:
        calls.get() == 1
        received.isEmpty()
    }

    void 'toPublisher emits the value once, for requests made before and after completion'() {
        given:
        def stage = new CompletableFuture<String>()
        def received = []
        org.reactivestreams.Subscription subscription
        CompletionStagePublishers.toPublisher { stage }.subscribe(new org.reactivestreams.Subscriber<String>() {
            void onSubscribe(org.reactivestreams.Subscription s) { subscription = s }
            void onNext(String s) { received << s }
            void onError(Throwable t) { received << t }
            void onComplete() { received << 'complete' }
        })

        when:
        subscription.request(1)
        stage.complete('value')
        subscription.request(1)

        then:
        received == ['value', 'complete']
    }

    void 'toPublisher fails a non-positive request without obtaining the stage'() {
        given:
        def received = []
        def calls = new AtomicInteger()
        CompletionStagePublishers.toPublisher {
            calls.incrementAndGet()
            CompletableFuture.completedFuture('value')
        }.subscribe(new org.reactivestreams.Subscriber<String>() {
            void onSubscribe(org.reactivestreams.Subscription s) { s.request(-1) }
            void onNext(String s) { received << s }
            void onError(Throwable t) { received << t.class }
            void onComplete() { received << 'complete' }
        })

        expect:
        received == [IllegalArgumentException]
        calls.get() == 0
    }
}
