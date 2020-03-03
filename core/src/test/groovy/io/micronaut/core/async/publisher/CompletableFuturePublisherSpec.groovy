package io.micronaut.core.async.publisher

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class CompletableFuturePublisherSpec extends Specification {

    void "test the error is emitted when thrown from the supplier"() {
        when:
        Publisher publisher = Publishers.fromCompletableFuture({ -> throw new RuntimeException() })
        PollingConditions conditions = new PollingConditions()
        Throwable error
        publisher.subscribe(new Subscriber<Object>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
            }

            @Override
            void onNext(Object o) {}

            @Override
            void onError(Throwable t) {
                error = t
            }

            @Override
            void onComplete() {}
        })

        then:
        conditions.eventually {
            error instanceof RuntimeException
        }
    }
}
