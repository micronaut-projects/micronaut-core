package io.micronaut.core.async.publisher


import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

class DelayedSubscriberSpec extends Specification {
    def "downstream first simple"() {
        given:
        def upstream = new Upstream()
        def downstream = new Downstream()
        def delayedSubscriber = new DelayedSubscriber<String>()

        when:
        delayedSubscriber.subscribe(downstream)
        then:
        downstream.subscription != null
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete

        when:
        downstream.subscription.request(1)
        then:
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete

        when:
        delayedSubscriber.onSubscribe(upstream)
        then:
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete
        upstream.req == 1
        !upstream.cancelled

        when:
        delayedSubscriber.onNext("foo")
        then:
        downstream.items == ["foo"]
        downstream.error == null
        !downstream.complete

        when:
        delayedSubscriber.onComplete()
        then:
        downstream.error == null
        downstream.complete
    }

    def "upstream first simple"() {
        given:
        def upstream = new Upstream()
        def downstream = new Downstream()
        def delayedSubscriber = new DelayedSubscriber<String>()

        when:
        delayedSubscriber.onSubscribe(upstream)
        then:
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete

        when:
        delayedSubscriber.onComplete()
        then:
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete

        when:
        delayedSubscriber.subscribe(downstream)
        then:
        downstream.items.isEmpty()
        downstream.error == null
        downstream.complete
    }

    def "upstream first, request in onSubscribe"() {
        given:
        def upstream = new Upstream()
        def downstream = new Downstream() {
            @Override
            void onSubscribe(Subscription s) {
                enter()
                s.request(1)
                assert upstream.req == 0
                exit()
            }
        }
        def delayedSubscriber = new DelayedSubscriber<String>()

        when:
        delayedSubscriber.onSubscribe(upstream)
        then:
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete

        when:
        delayedSubscriber.onComplete()
        then:
        downstream.items.isEmpty()
        downstream.error == null
        !downstream.complete

        when:
        delayedSubscriber.subscribe(downstream)
        then:
        downstream.items.isEmpty()
        downstream.error == null
        downstream.complete
        upstream.req == 1
    }

    class Upstream implements Subscription {
        long req = 0
        boolean cancelled = false

        @Override
        void request(long n) {
            req += n
        }

        @Override
        void cancel() {
            cancelled = true
        }
    }

    class Downstream implements Subscriber<String> {
        boolean wip
        Subscription subscription
        List<String> items = new ArrayList<>()
        Throwable error
        boolean complete

        void enter() {
            if (wip) {
                throw new IllegalStateException("Already working")
            }
            wip = true
        }

        void exit() {
            assert wip
            wip = false
        }

        @Override
        void onSubscribe(Subscription s) {
            enter()
            this.subscription = s
            exit()
        }

        @Override
        void onNext(String s) {
            enter()
            items.add(s)
            exit()
        }

        @Override
        void onError(Throwable t) {
            enter()
            error = t
            exit()
        }

        @Override
        void onComplete() {
            enter()
            complete = true
            exit()
        }
    }
}
