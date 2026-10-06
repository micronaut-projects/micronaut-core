package io.micronaut.http.server

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ResponsePiecesSpec extends Specification {

    static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)

    def 'the flow completes with the first piece, and the pieces are emitted in order, under the demand of the subscriber'() {
        given:
        def requested = []
        def source = Flux.range(1, 5).doOnRequest { requested << it }

        when:
        def flow = ResponsePieces.write(source, { ExecutionFlow.just(piece("v" + it)) }, {})

        then: 'the first item is written before anyone subscribes'
        requested == [1]
        flow.tryCompleteValue() != null

        when:
        def subscriber = subscribe(flow)
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

    def 'a body without items completes the flow, and the pieces complete without demand'() {
        given:
        def ends = 0

        when:
        def flow = ResponsePieces.write(Flux.empty(), { ExecutionFlow.just(piece("v" + it)) }, { ends++ })
        def subscriber = subscribe(flow)

        then:
        subscriber.items == []
        subscriber.completed
        ends == 1
    }

    def 'a failure before the first piece fails the flow and ends the pieces'() {
        given:
        def ends = 0
        def failure = new IllegalStateException("boom")

        when:
        def flow = ResponsePieces.write(Flux.error(failure), { ExecutionFlow.just(piece("v" + it)) }, { ends++ })

        then:
        flow.tryCompleteError().is(failure)
        ends == 1
    }

    def 'a failure to write the first piece fails the flow and cancels the body'() {
        given:
        def cancelled = false
        def failure = new IllegalStateException("boom")

        when:
        def flow = ResponsePieces.write(Flux.range(0, 5).doOnCancel { cancelled = true }, { ExecutionFlow.error(failure) }, {})

        then:
        flow.tryCompleteError().is(failure)
        cancelled
    }

    def 'cancelling the flow before the first piece cancels the body and ends the pieces'() {
        given:
        def flow = DelayedExecutionFlow.<CloseableByteBody> create()
        def flowCancelled = false
        flow.onCancel { flowCancelled = true }
        def bodyCancelled = false
        def ends = 0
        def result = ResponsePieces.write(Flux.range(0, 3).doOnCancel { bodyCancelled = true }, { flow }, { ends++ })

        when:
        result.cancel()

        then:
        bodyCancelled
        flowCancelled
        ends == 1
    }

    def 'cancelling the flow after the pieces were taken leaves them to their subscriber'() {
        given:
        def bodyCancelled = false
        def ends = 0
        def result = ResponsePieces.write(Flux.range(0, 3).doOnCancel { bodyCancelled = true }, { ExecutionFlow.just(piece("v" + it)) }, { ends++ })
        def subscriber = subscribe(result)

        when:
        result.cancel()
        subscriber.request(10)

        then:
        !bodyCancelled
        subscriber.items == ["v0", "v1", "v2"]
        subscriber.completed
        ends == 1
    }

    def 'a pending flow holds back the next item and the completion'() {
        given:
        def flows = [DelayedExecutionFlow.<CloseableByteBody> create(), DelayedExecutionFlow.<CloseableByteBody> create()]
        def requests = 0
        def source = Flux.range(0, 2).doOnRequest { requests++ }
        def result = ResponsePieces.write(source, { flows[it] }, {})

        expect:
        result.tryCompleteValue() == null
        requests == 1

        when:
        flows[0].complete(piece("a"))
        def subscriber = subscribe(result)
        subscriber.request(10)

        then:
        subscriber.items == ["a"]
        requests == 2
        !subscriber.completed

        when:
        flows[1].complete(piece("b"))

        then:
        subscriber.items == ["a", "b"]
        subscriber.completed
    }

    def 'a flow that completes without a value is skipped'() {
        when:
        def subscriber = subscribe(ResponsePieces.write(Flux.range(0, 3), { it == 1 ? ExecutionFlow.<CloseableByteBody> empty() : ExecutionFlow.just(piece("v" + it)) }, {}))
        subscriber.request(2)

        then:
        subscriber.items == ["v0", "v2"]
        subscriber.completed
    }

    def 'a failed flow fails the pieces and cancels the body'() {
        given:
        def cancelled = false
        def source = Flux.range(0, 5).doOnCancel { cancelled = true }
        def failure = new IllegalStateException("boom")
        def ends = 0

        when:
        def subscriber = subscribe(ResponsePieces.write(source, { it == 1 ? ExecutionFlow.<CloseableByteBody> error(failure) : ExecutionFlow.just(piece("v" + it)) }, { ends++ }))
        subscriber.request(Long.MAX_VALUE)

        then:
        subscriber.items == ["v0"]
        subscriber.error.is(failure)
        cancelled
        !subscriber.completed
        ends == 1
    }

    def 'a writer that throws fails the pieces and closes the item, and the items the body drops are closed'() {
        given:
        def items = (0..4).collect { new TrackedBody("v" + it) }
        def failure = new IllegalStateException("boom")

        when:
        def subscriber = subscribe(ResponsePieces.<TrackedBody> write(Flux.fromIterable(items), { if (it.text == "v2") throw failure; ExecutionFlow.just(piece(it.text)) }, {}))
        subscriber.request(Long.MAX_VALUE)

        then:
        subscriber.items == ["v0", "v1"]
        subscriber.error.is(failure)
        // the item that failed, and the items the body discards once it is cancelled
        items*.closed == [false, false, true, true, true]
    }

    def 'a body error while a flow is pending is delivered after its piece'() {
        given:
        def flows = [DelayedExecutionFlow.<CloseableByteBody> create(), DelayedExecutionFlow.<CloseableByteBody> create()]
        def sink = Sinks.many().unicast().<Integer> onBackpressureBuffer()
        def failure = new IllegalStateException("upstream")
        def result = ResponsePieces.write(sink.asFlux(), { flows[it] }, {})
        sink.tryEmitNext(0)
        flows[0].complete(piece("first"))
        def subscriber = subscribe(result)
        subscriber.request(10)

        when:
        sink.tryEmitNext(1)
        sink.tryEmitError(failure)

        then:
        subscriber.error == null

        when:
        def late = piece("late")
        flows[1].complete(late)

        then:
        subscriber.items == ["first", "late"]
        subscriber.error.is(failure)
    }

    def 'the piece of a flow that completes after cancellation is closed'() {
        given:
        def flows = [DelayedExecutionFlow.<CloseableByteBody> create(), DelayedExecutionFlow.<CloseableByteBody> create()]
        def subscriber = subscribe(complete(ResponsePieces.write(Flux.range(0, 3), { flows[it] }, {}), flows[0]))
        subscriber.request(10)

        when:
        subscriber.subscription.cancel()
        def late = new TrackedBody("late")
        flows[1].complete(late)

        then:
        subscriber.items == ["first"]
        late.closed
        !subscriber.completed
        subscriber.error == null
    }

    def 'cancelling the subscription cancels the pending flow and the body, and ends the pieces once'() {
        given:
        def flows = [DelayedExecutionFlow.<CloseableByteBody> create(), DelayedExecutionFlow.<CloseableByteBody> create()]
        def flowCancelled = false
        flows[1].onCancel { flowCancelled = true }
        def bodyCancelled = false
        def ends = 0
        def subscriber = subscribe(complete(ResponsePieces.write(Flux.range(0, 3).doOnCancel { bodyCancelled = true }, { flows[it] }, { ends++ }), flows[0]))
        subscriber.request(10)

        when:
        subscriber.subscription.cancel()
        subscriber.subscription.cancel()

        then:
        flowCancelled
        bodyCancelled
        ends == 1
        subscriber.items == ["first"]
        !subscriber.completed
        subscriber.error == null
    }

    def 'a flow returned after the pieces were cancelled is cancelled, and its piece closed'() {
        given:
        def flow = DelayedExecutionFlow.<CloseableByteBody> create()
        def flowCancelled = false
        flow.onCancel { flowCancelled = true }
        def subscriber = new RecordingSubscriber()
        def result = ResponsePieces.write(Flux.range(0, 3), {
            if (it == 0) {
                return ExecutionFlow.just(piece("first"))
            }
            // the subscriber cancels while the writer runs
            subscriber.subscription.cancel()
            flow
        }, {})
        result.tryCompleteValue().subscribe(subscriber)

        when:
        subscriber.request(10)
        def late = new TrackedBody("late")
        flow.complete(late)

        then:
        flowCancelled
        late.closed
        subscriber.items == ["first"]
        !subscriber.completed
        subscriber.error == null
    }

    def 'a piece that waits for demand is closed when the subscription is cancelled'() {
        given:
        def first = new TrackedBody("first")
        def subscriber = subscribe(ResponsePieces.write(Flux.just(1), { ExecutionFlow.just(first) }, {}))

        when:
        subscriber.subscription.cancel()

        then:
        first.closed
        subscriber.items == []
    }

    def 'a second subscriber is rejected'() {
        given:
        def flow = ResponsePieces.write(Flux.just(1), { ExecutionFlow.just(piece("v" + it)) }, {})
        subscribe(flow)

        when:
        def second = subscribe(flow)

        then:
        second.error instanceof IllegalStateException
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
        ResponsePieces.write(Flux.range(0, 1000), { ExecutionFlow.just(piece("v" + it)) }, {}).tryCompleteValue().subscribe(subscriber)
        subscriber.request(1)

        then:
        subscriber.items.size() == 1000
        subscriber.completed
        maxDepth == 1
    }

    private static CloseableByteBody piece(String text) {
        return FACTORY.adapt(text.getBytes(StandardCharsets.UTF_8))
    }

    private static ExecutionFlow<Publisher<ByteBody>> complete(ExecutionFlow<Publisher<ByteBody>> result, DelayedExecutionFlow<CloseableByteBody> first) {
        first.complete(piece("first"))
        return result
    }

    private static RecordingSubscriber subscribe(ExecutionFlow<Publisher<ByteBody>> flow) {
        def subscriber = new RecordingSubscriber()
        flow.tryCompleteValue().subscribe(subscriber)
        return subscriber
    }

    /**
     * A piece that records whether it was closed.
     */
    static class TrackedBody implements CloseableByteBody {
        final String text
        @Delegate(excludes = ["close", "move"])
        final CloseableByteBody delegate
        boolean closed

        TrackedBody(String text) {
            this.text = text
            this.delegate = piece(text)
        }

        @Override
        void close() {
            closed = true
            delegate.close()
        }

        @Override
        CloseableByteBody move() {
            return this
        }
    }

    static class RecordingSubscriber implements Subscriber<ByteBody> {
        Subscription subscription
        List<String> items = []
        Throwable error
        boolean completed
        Closure onItem = {}

        void request(long n) {
            subscription.request(n)
        }

        @Override
        void onSubscribe(Subscription s) {
            subscription = s
        }

        @Override
        void onNext(ByteBody body) {
            items << new String(((AvailableByteBody) body).toByteArray(), StandardCharsets.UTF_8)
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
