package io.micronaut.http.client.netty

import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Mono
import spock.lang.Specification

class CancellableMonoSinkSpec extends Specification {
    def "cancel before request"() {
        given:
        def sink = new CancellableMonoSink<String>(null)
        def result = "unset"
        Subscription subscription = null
        sink.subscribe(new Subscriber<String>() {
            @Override
            void onSubscribe(Subscription s) {
                subscription = s
            }

            @Override
            void onNext(String s) {
                result = s
            }

            @Override
            void onError(Throwable t) {
            }

            @Override
            void onComplete() {
            }
        })

        when:
        sink.cancel()
        subscription.request(1)

        then:
        result == "unset"
    }
}
