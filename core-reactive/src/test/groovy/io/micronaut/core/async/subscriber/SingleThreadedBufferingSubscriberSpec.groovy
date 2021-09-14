package io.micronaut.core.async.subscriber

import org.reactivestreams.Subscription
import spock.lang.Specification
import spock.lang.Timeout

class SingleThreadedBufferingSubscriberSpec extends Specification {
    // this timeout doesn't actually work, because it only calls .interrupt() which SingleThreadedBufferingSubscriber
    // doesn't handle, but it at least prints a message.
    @Timeout(10)
    def 'receive demand in buffering state'() {
        given:
        def seen = new ArrayList<String>()
        def subscriber = new SingleThreadedBufferingSubscriber<String>() {
            @Override
            protected void doOnSubscribe(Subscription subscription) {
            }

            @Override
            protected void doOnNext(String message) {
                seen.add(message)
            }

            @Override
            protected void doOnError(Throwable t) {
            }

            @Override
            protected void doOnComplete() {
            }
        }
        def downstreamSubscription = subscriber.newDownstreamSubscription()
        subscriber.onSubscribe(new Subscription() {
            @Override
            void request(long n) {
            }

            @Override
            void cancel() {
            }
        })

        when:
        subscriber.onNext('foo')
        then:
        seen == []

        when:
        downstreamSubscription.request(1)
        then:
        seen == ['foo']
    }
}
