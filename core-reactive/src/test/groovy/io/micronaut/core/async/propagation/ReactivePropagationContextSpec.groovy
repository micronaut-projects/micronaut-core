package io.micronaut.core.async.propagation

import io.micronaut.core.async.subscriber.LazySendingSubscriber
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import reactor.core.CoreSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.util.context.Context
import reactor.util.context.ContextView
import spock.lang.Specification

/**
 * The Reactor context of a propagating subscriber: the downstream context with the propagated
 * context added, following the downstream context when it changes.
 */
class ReactivePropagationContextSpec extends Specification {

    def "the context follows a downstream context that changes after subscription"() {
        given:
        PropagatedContext propagatedContext = PropagatedContext.empty().plus(new Element())
        MutableContextSubscriber downstream = new MutableContextSubscriber()
        CoreSubscriber<Object> subscriber = ReactivePropagation.propagate(propagatedContext, downstream) as CoreSubscriber<Object>

        when:
        Context before = subscriber.currentContext()
        downstream.context = Context.of("key", "value")
        Context after = subscriber.currentContext()

        then:
        ReactorPropagation.findPropagatedContext(before).get() == propagatedContext
        !before.hasKey("key")
        ReactorPropagation.findPropagatedContext(after).get() == propagatedContext
        after.get("key") == "value"
    }

    def "the context is reused while the downstream context does not change"() {
        given:
        PropagatedContext propagatedContext = PropagatedContext.empty().plus(new Element())
        MutableContextSubscriber downstream = new MutableContextSubscriber(context: Context.of("key", "value"))
        CoreSubscriber<Object> subscriber = ReactivePropagation.propagate(propagatedContext, downstream) as CoreSubscriber<Object>

        expect:
        subscriber.currentContext().is(subscriber.currentContext())
    }

    def "a lazy sending subscriber's downstream context reaches the source"() {
        given:
        PropagatedContext propagatedContext = PropagatedContext.empty().plus(new Element())
        FluxSink<String> sink = null
        Flux<String> source = Flux.create { sink = it }
        Publisher<String> lazy = null

        when: 'the first element is sent before the lazy subscriber has a downstream'
        LazySendingSubscriber.create(ReactivePropagation.propagate(propagatedContext, source))
            .onComplete { publisher, error -> lazy = publisher }
        sink.next("first")
        MutableContextSubscriber downstream = new MutableContextSubscriber(context: Context.of("key", "value"))
        lazy.subscribe(downstream)
        ContextView contextView = sink.contextView()

        then:
        contextView.get("key") == "value"
        ReactorPropagation.findPropagatedContext(contextView).get() == propagatedContext
    }

    static class Element implements PropagatedContextElement {
    }

    static class MutableContextSubscriber implements CoreSubscriber<Object> {
        Context context = Context.empty()

        @Override
        Context currentContext() {
            return context
        }

        @Override
        void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE)
        }

        @Override
        void onNext(Object o) {
        }

        @Override
        void onError(Throwable t) {
        }

        @Override
        void onComplete() {
        }
    }
}
