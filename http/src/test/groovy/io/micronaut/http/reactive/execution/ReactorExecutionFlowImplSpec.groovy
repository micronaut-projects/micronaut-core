package io.micronaut.http.reactive.execution

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.execution.ImperativeExecutionFlow
import io.micronaut.core.propagation.PropagatedContext
import org.reactivestreams.Publisher
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Issue
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

    def 'defuse immediate'() {
        when:
        Hooks.resetOnOperatorDebug()
        def flow = ReactorExecutionFlowImpl.defuse(ReactorExecutionFlowImpl.toMono(ExecutionFlow.just("foo")), PropagatedContext.empty())
        then:
        flow instanceof ImperativeExecutionFlow
        flow.tryCompleteValue() == "foo"
    }

    def 'defuse immediate with map'() {
        when:
        Hooks.resetOnOperatorDebug()
        def flow = ReactorExecutionFlowImpl.defuse(ReactorExecutionFlowImpl.toMono(ExecutionFlow.just("foo")).map { it + "bar" }, PropagatedContext.empty())
        then:
        flow instanceof ImperativeExecutionFlow
        flow.tryCompleteValue() == "foobar"
    }

    def 'defuse delayed with map'() {
        when:
        Hooks.resetOnOperatorDebug()
        DelayedExecutionFlow del = DelayedExecutionFlow.create()
        def flow = ReactorExecutionFlowImpl.defuse(ReactorExecutionFlowImpl.toMono(del).map { it + "bar" }, PropagatedContext.empty())
        def result
        flow.onComplete((o, e) -> {
            result = o
        })
        then:
        result == null
        when:
        del.complete("foo")
        then:
        result == "foobar"
    }

    def 'defuse delayed with map, but delayed completes before defuse'() {
        when:
        Hooks.resetOnOperatorDebug()
        DelayedExecutionFlow del = DelayedExecutionFlow.create()
        def mono = ReactorExecutionFlowImpl.toMono(del).map { it + "bar" }
        del.complete("foo")
        def flow = ReactorExecutionFlowImpl.defuse(mono, PropagatedContext.empty())
        then:
        flow instanceof ImperativeExecutionFlow
        flow.tryCompleteValue() == "foobar"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11744")
    def 'double cancel'() {
        given:
        Hooks.resetOnOperatorDebug()
        def cancelled = false
        def flow = ReactiveExecutionFlow.fromPublisher(Mono.just("foo").doOnCancel { cancelled = true })
        flow.onComplete {}

        when:
        flow.cancel()
        then:
        cancelled

        when:
        flow.cancel()
        then:
        noExceptionThrown()
    }
}
