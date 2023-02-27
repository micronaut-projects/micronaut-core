package io.micronaut.http.reactive.execution

import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Specification

import java.time.Duration

class ReactorExecutionFlowImplSpec extends Specification {
    /*
     I tried to improve the heuristic with the following code:

        if (value instanceof Scannable scannable &&
            scannable.scan(Scannable.Attr.RUN_STYLE) == Scannable.Attr.RunStyle.SYNC &&
            scannable.parents().allMatch(s -> s.scan(Scannable.Attr.RUN_STYLE) == Scannable.Attr.RunStyle.SYNC)) {

            ImmediateSubscriber immediateSubscriber = new ImmediateSubscriber();
            value.subscribe(immediateSubscriber);
            if (!immediateSubscriber.done) {
                throw new IllegalStateException("Scan showed the value would be synchronous, but it wasn't?");
            }
            return immediateSubscriber.result;
        }

    private static class ImmediateSubscriber implements CoreSubscriber<Object> {
        ImperativeExecutionFlow<Object> result;
        boolean done = false;

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Object o) {
            if (result != null) {
                throw new IllegalStateException("Duplicate result");
            }
            result = (ImperativeExecutionFlow<Object>) ExecutionFlow.just(o);
        }

        @Override
        public void onError(Throwable t) {
            result = (ImperativeExecutionFlow<Object>) ExecutionFlow.error(t);
            done = true;
        }

        @Override
        public void onComplete() {
            if (result == null) {
                result = (ImperativeExecutionFlow<Object>) ExecutionFlow.error(new NoSuchElementException("Mono was empty"));
            }
            done = true;
        }
    }

    However it turns out that some operators (e.g. the delaySubscription below) show up as "SYNC" even though they don't
    yield an immediate result.

    There is also another api, OptimizableOperator, which could be used for this. However it is private, and it would
    not give certainty whether an immediate execution is possible before actually subscribing, which is necessary for
    the ExecutionFlow api (only one subscription allowed).
     */

    def 'test immediate'(Publisher<String> publisher) {
        given:
        def flow = ReactiveExecutionFlow.fromPublisher(publisher)

        when:
        def done = flow.tryComplete()
        then:
        done != null
        done.value == 'foo'

        where:
        publisher << [
                Mono.just("foo"),
                // not supported by our current algorithm
                // Mono.just("f").map { it + "oo" }
        ]
    }

    def 'test not immediate'(Publisher<String> publisher) {
        given:
        def flow = ReactiveExecutionFlow.fromPublisher(publisher)

        when:
        def done = flow.tryComplete()
        then:
        done == null

        where:
        publisher << [
                Mono.just("foo").delayElement(Duration.ofSeconds(1)),
                Mono.just("foo").delaySubscription(Duration.ofSeconds(1)),
                Mono.just("foo").subscribeOn(Schedulers.immediate()),
        ]
    }
}
