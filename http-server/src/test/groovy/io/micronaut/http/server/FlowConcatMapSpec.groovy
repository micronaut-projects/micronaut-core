package io.micronaut.http.server

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import org.reactivestreams.Subscription
import reactor.core.CoreSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.util.context.Context
import spock.lang.Specification

class FlowConcatMapSpec extends Specification {

    def 'completed flows are emitted in order, under the demand of the subscriber'() {
        given:
        def requested = []
        def source = Flux.range(1, 5).doOnRequest { requested << it }
        def subscriber = new RecordingSubscriber()

        when:
        new FlowConcatMap<Integer, String>(source, { ExecutionFlow.just("v" + it) }).subscribe(subscriber)
        subscriber.request(2)

        then:
        subscriber.items == ["v1", "v2"]
        !subscriber.completed

        and: 'items are requested one at a time'
        requested.every { it == 1 }

        when:
        subscriber.request(Long.MAX_VALUE)

        then:
        subscriber.items == ["v1", "v2", "v3", "v4", "v5"]
        subscriber.completed
        subscriber.error == null
    }

    def 'a pending flow holds back the next item and the completion'() {
        given:
        def flows = [DelayedExecutionFlow.create(), DelayedExecutionFlow.create()]
        def requests = 0
        def source = Flux.range(0, 2).doOnRequest { requests++ }
        def subscriber = new RecordingSubscriber()
        new FlowConcatMap<Integer, String>(source, { flows[it] }).subscribe(subscriber)

        when:
        subscriber.request(10)

        then:
        subscriber.items == []
        requests == 1

        when:
        flows[0].complete("a")

        then:
        subscriber.items == ["a"]
        requests == 2
        !subscriber.completed

        when:
        flows[1].complete("b")

        then:
        subscriber.items == ["a", "b"]
        subscriber.completed
    }

    def 'a flow that completes without a value is skipped'() {
        given:
        def subscriber = new RecordingSubscriber()

        when:
        new FlowConcatMap<Integer, String>(Flux.range(0, 3), { it == 1 ? ExecutionFlow.empty() : ExecutionFlow.just("v" + it) }).subscribe(subscriber)
        subscriber.request(2)

        then:
        subscriber.items == ["v0", "v2"]
        subscriber.completed
    }

    def 'a failed flow fails the stream and cancels the upstream'() {
        given:
        def cancelled = false
        def source = Flux.range(0, 5).doOnCancel { cancelled = true }
        def subscriber = new RecordingSubscriber()
        def failure = new IllegalStateException("boom")

        when:
        new FlowConcatMap<Integer, String>(source, { it == 1 ? ExecutionFlow.error(failure) : ExecutionFlow.just("v" + it) }).subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        then:
        subscriber.items == ["v0"]
        subscriber.error.is(failure)
        cancelled
        !subscriber.completed
    }

    def 'a mapper that throws fails the stream and discards the item'() {
        given:
        def discarded = []
        def subscriber = new RecordingSubscriber(Context.of("reactor.onDiscard.local", { discarded << it } as java.util.function.Consumer))
        def failure = new IllegalStateException("boom")

        when:
        new FlowConcatMap<Integer, String>(Flux.range(0, 5), { if (it == 2) throw failure; ExecutionFlow.just("v" + it) }).subscribe(subscriber)
        subscriber.request(Long.MAX_VALUE)

        then:
        subscriber.items == ["v0", "v1"]
        subscriber.error.is(failure)
        discarded == [2]
    }

    def 'an upstream error while a flow is pending is delivered after its result'() {
        given:
        def flow = DelayedExecutionFlow.<String> create()
        def sink = Sinks.many().unicast().<Integer> onBackpressureBuffer()
        def discarded = []
        def subscriber = new RecordingSubscriber(Context.of("reactor.onDiscard.local", { discarded << it } as java.util.function.Consumer))
        def failure = new IllegalStateException("upstream")
        new FlowConcatMap<Integer, String>(sink.asFlux(), { flow }).subscribe(subscriber)
        subscriber.request(10)

        when:
        sink.tryEmitNext(1)
        sink.tryEmitError(failure)

        then:
        subscriber.error == null

        when:
        flow.complete("late")

        then:
        subscriber.items == ["late"]
        subscriber.error.is(failure)
        discarded == []
    }

    def 'the result of a flow that completes after cancellation is discarded'() {
        given:
        def flow = DelayedExecutionFlow.<String> create()
        def discarded = []
        def subscriber = new RecordingSubscriber(Context.of("reactor.onDiscard.local", { discarded << it } as java.util.function.Consumer))
        new FlowConcatMap<Integer, String>(Flux.range(0, 3), { flow }).subscribe(subscriber)
        subscriber.request(10)

        when:
        subscriber.subscription.cancel()
        flow.complete("late")

        then:
        subscriber.items == []
        discarded == ["late"]
        !subscriber.completed
        subscriber.error == null
    }

    def 'cancelling the subscription cancels the pending flow'() {
        given:
        def flow = DelayedExecutionFlow.<String> create()
        def flowCancelled = false
        flow.onCancel { flowCancelled = true }
        def upstreamCancelled = false
        def subscriber = new RecordingSubscriber()
        new FlowConcatMap<Integer, String>(Flux.range(0, 3).doOnCancel { upstreamCancelled = true }, { flow }).subscribe(subscriber)
        subscriber.request(10)

        when:
        subscriber.subscription.cancel()

        then:
        flowCancelled
        upstreamCancelled
        subscriber.items == []
        !subscriber.completed
        subscriber.error == null
    }

    def 'a flow returned after the subscription was cancelled is cancelled'() {
        given:
        def flow = DelayedExecutionFlow.<String> create()
        def flowCancelled = false
        flow.onCancel { flowCancelled = true }
        def discarded = []
        def subscriber = new RecordingSubscriber(Context.of("reactor.onDiscard.local", { discarded << it } as java.util.function.Consumer))
        new FlowConcatMap<Integer, String>(Flux.range(0, 3), {
            // the subscriber cancels while the mapper runs
            subscriber.subscription.cancel()
            flow
        }).subscribe(subscriber)

        when:
        subscriber.request(10)
        flow.complete("late")

        then:
        flowCancelled
        subscriber.items == []
        discarded == ["late"]
        !subscriber.completed
        subscriber.error == null
    }

    def 'a subscriber may request from onNext without reentrant emission'() {
        given:
        def depth = 0
        def maxDepth = 0
        def subscriber = new RecordingSubscriber()
        subscriber.onItem = {
            depth++
            maxDepth = Math.max(maxDepth, depth)
            subscriber.request(1)
            depth--
        }

        when:
        new FlowConcatMap<Integer, String>(Flux.range(0, 1000), { ExecutionFlow.just("v" + it) }).subscribe(subscriber)
        subscriber.request(1)

        then:
        subscriber.items.size() == 1000
        subscriber.completed
        maxDepth == 1
    }

    static class RecordingSubscriber implements CoreSubscriber<String> {
        final Context context
        Subscription subscription
        List<String> items = []
        Throwable error
        boolean completed
        Closure onItem = {}

        RecordingSubscriber(Context context = Context.empty()) {
            this.context = context
        }

        @Override
        Context currentContext() {
            return context
        }

        void request(long n) {
            subscription.request(n)
        }

        @Override
        void onSubscribe(Subscription s) {
            subscription = s
        }

        @Override
        void onNext(String s) {
            items << s
            onItem.call()
        }

        @Override
        void onError(Throwable t) {
            error = t
        }

        @Override
        void onComplete() {
            completed = true
        }
    }
}
