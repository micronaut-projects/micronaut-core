package io.micronaut.http.client.netty

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Sinks
import spock.lang.Specification

class CancellableMonoSinkContractSpec extends Specification {

    def "a second subscriber is rejected and the first still receives the value"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def first = new RecordingSubscriber()
        def second = new RecordingSubscriber()

        when:
        sink.subscribe(first)
        sink.subscribe(second)

        then:
        second.signals.size() == 2
        second.signals[0] == "onSubscribe"
        second.signals[1].startsWith("onError:IllegalStateException")
        first.signals == ["onSubscribe"]

        when:
        first.subscription.request(1)
        def result = sink.tryEmitValue("foo")

        then:
        result == Sinks.EmitResult.OK
        first.signals == ["onSubscribe", "onNext:foo", "onComplete"]
        second.signals.size() == 2
    }

    def "error is delivered without demand"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def subscriber = new RecordingSubscriber()
        sink.subscribe(subscriber)

        when:
        def result = sink.tryEmitError(new RuntimeException("boom"))

        then:
        result == Sinks.EmitResult.OK
        subscriber.signals == ["onSubscribe", "onError:RuntimeException:boom"]
    }

    def "error emitted before subscribe is delivered without demand"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        sink.tryEmitError(new RuntimeException("boom"))
        def subscriber = new RecordingSubscriber()

        when:
        sink.subscribe(subscriber)
        subscriber.subscription.request(1)

        then:
        subscriber.signals == ["onSubscribe", "onError:RuntimeException:boom"]
    }

    def "empty completion is delivered without demand"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def subscriber = new RecordingSubscriber()
        sink.subscribe(subscriber)

        when:
        def result = sink.tryEmitEmpty()

        then:
        result == Sinks.EmitResult.OK
        subscriber.signals == ["onSubscribe", "onComplete"]

        when:
        subscriber.subscription.request(1)

        then:
        subscriber.signals == ["onSubscribe", "onComplete"]
    }

    def "value is only delivered after demand"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def subscriber = new RecordingSubscriber()
        sink.subscribe(subscriber)

        when:
        sink.tryEmitValue("foo")

        then:
        subscriber.signals == ["onSubscribe"]

        when:
        subscriber.subscription.request(1)
        subscriber.subscription.request(1)

        then:
        subscriber.signals == ["onSubscribe", "onNext:foo", "onComplete"]
    }

    def "non-positive demand #n signals an error and cancels"(long n) {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def subscriber = new RecordingSubscriber()
        sink.subscribe(subscriber)

        when:
        subscriber.subscription.request(n)

        then:
        subscriber.signals.size() == 2
        subscriber.signals[1].startsWith("onError:IllegalArgumentException")

        when:
        def result = sink.tryEmitValue("foo")
        subscriber.subscription.request(1)

        then:
        result.isFailure()
        subscriber.signals.size() == 2

        where:
        n << [0L, -1L]
    }

    def "cancellation from onNext suppresses completion"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def subscriber = new RecordingSubscriber(cancelOnNext: true)
        sink.subscribe(subscriber)
        subscriber.subscription.request(1)

        when:
        def result = sink.tryEmitValue("foo")

        then:
        result == Sinks.EmitResult.OK
        subscriber.signals == ["onSubscribe", "onNext:foo"]
        sink.tryEmitValue("bar").isFailure()
    }

    static class RecordingSubscriber implements Subscriber<String> {
        List<String> signals = []
        Subscription subscription
        boolean cancelOnNext

        @Override
        void onSubscribe(Subscription s) {
            subscription = s
            signals << "onSubscribe"
        }

        @Override
        void onNext(String s) {
            signals << ("onNext:" + s)
            if (cancelOnNext) {
                subscription.cancel()
            }
        }

        @Override
        void onError(Throwable t) {
            signals << ("onError:" + t.class.simpleName + ":" + t.message)
        }

        @Override
        void onComplete() {
            signals << "onComplete"
        }
    }
}
