package io.micronaut.core.async.propagation

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.CorePublisher
import reactor.core.CoreSubscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import static io.micronaut.core.async.propagation.ReactorPropagation.addPropagatedContext

class ReactivePropagationSpec extends Specification {

    def "test context propagation on Publisher methods"() {
        given:
        PropagatedContext outerContext = PropagatedContext.empty()
        PropagatedContext innerContext = PropagatedContext.empty().plus(new PropagatedElement())
        ContextCapturingPublisher<Object> publisher = new ContextCapturingPublisher<>()
        Subscriber<Object> subscriber = new EmptySubscriber<Object>()

        when:
        try (def ignore = outerContext.propagate()) {
            ReactivePropagation.propagate(innerContext, publisher).subscribe(subscriber)
        }

        then:
        publisher.capturedContext == innerContext
    }

    def "test context propagation on Subscriber methods directly"() {
        given:
        PropagatedContext outerContext = PropagatedContext.empty()
        PropagatedContext innerContext = PropagatedContext.empty().plus(new PropagatedElement())
        Publisher<Object> publisher = new CallAllSubscriberMethodInContext<>(outerContext)
        ContextCapturingSubscriber<Object> subscriber = new ContextCapturingSubscriber<>()

        when:
        publisher.subscribe(ReactivePropagation.propagate(innerContext, subscriber))

        then:
        subscriber.contextCapturedOnSubscribe == innerContext
        subscriber.contextsCapturedOnNext == [innerContext]
        subscriber.contextCapturedOnError == innerContext
        subscriber.contextCapturedOnComplete == innerContext
    }

    def "test context propagation on Subscriber methods via a propagated Publisher"() {
        given:
        PropagatedContext outerContext = PropagatedContext.empty()
        PropagatedContext innerContext = PropagatedContext.empty().plus(new PropagatedElement())
        Publisher<Object> publisher = new CallAllSubscriberMethodInContext<>(outerContext)
        ContextCapturingSubscriber<Object> subscriber = new ContextCapturingSubscriber<>()

        when:
        ReactivePropagation.propagate(innerContext, publisher).subscribe(subscriber)

        then:
        subscriber.contextCapturedOnSubscribe == innerContext
        subscriber.contextsCapturedOnNext == [innerContext]
        subscriber.contextCapturedOnError == innerContext
        subscriber.contextCapturedOnComplete == innerContext
    }

    def "test context propagation on CoreSubscriber methods via a propagated CorePublisher"() {
        given:
        PropagatedContext outerContext = PropagatedContext.empty()
        PropagatedContext innerContext = PropagatedContext.empty().plus(new PropagatedElement())
        Publisher<Object> publisher = new CallAllSubscriberMethodInContext<>(outerContext)
        ContextCapturingSubscriber<Object> subscriber = new ContextCapturingSubscriber<>()

        when:
        ReactivePropagation.propagate(innerContext, new CorePublisherAdapter<>(publisher))
                .subscribe(new CoreSubscriberAdapter<>(subscriber))

        then:
        subscriber.contextCapturedOnSubscribe == innerContext
        subscriber.contextsCapturedOnNext == [innerContext]
        subscriber.contextCapturedOnError == innerContext
        subscriber.contextCapturedOnComplete == innerContext
    }

    def "test context propagation on Subscriber methods via a propagated Mono"() {
        given:
        PropagatedContext outerContext = PropagatedContext.empty()
        PropagatedContext innerContext = PropagatedContext.empty().plus(new PropagatedElement())
        Publisher<Object> publisher = Mono.just('element').contextWrite { addPropagatedContext(it, outerContext) }
        ContextCapturingSubscriber<Object> subscriber = new ContextCapturingSubscriber<>()

        when:
        ReactivePropagation.propagate(innerContext, publisher).subscribe(subscriber)

        then:
        subscriber.contextCapturedOnSubscribe == innerContext

        when:
        subscriber.subscription.request(1)

        then:
        subscriber.contextsCapturedOnNext == [innerContext]
        subscriber.contextCapturedOnComplete == innerContext
    }

    def "test context propagation on Subscriber methods via a propagated Flux"() {
        given:
        PropagatedContext outerContext = PropagatedContext.empty()
        PropagatedContext innerContext = PropagatedContext.empty().plus(new PropagatedElement())
        Publisher<Object> publisher = Flux.just('one', 'two', 'three').contextWrite { addPropagatedContext(it, outerContext) }
        ContextCapturingSubscriber<Object> subscriber = new ContextCapturingSubscriber<>()

        when:
        ReactivePropagation.propagate(innerContext, publisher).subscribe(subscriber)

        then:
        subscriber.contextCapturedOnSubscribe == innerContext

        when:
        subscriber.subscription.request(3)

        then:
        subscriber.contextsCapturedOnNext == [innerContext, innerContext, innerContext]
        subscriber.contextCapturedOnComplete == innerContext
    }

    static class CallAllSubscriberMethodInContext<T> implements Publisher<T> {

        final PropagatedContext context

        CallAllSubscriberMethodInContext(PropagatedContext context) {
            this.context = context
        }

        @Override
        void subscribe(Subscriber<? super T> subscriber) {
            try (def ignore = context.propagate()) {
                subscriber.onSubscribe(null)
                subscriber.onNext(null)
                subscriber.onError(null)
                subscriber.onComplete()
            }
        }
    }

    static class ContextCapturingPublisher<T> implements Publisher<T> {
        PropagatedContext capturedContext

        @Override
        void subscribe(Subscriber<? super T> subscriber) {
            capturedContext = PropagatedContext.getOrEmpty()
        }
    }

    static class ContextCapturingSubscriber<T> implements Subscriber<T> {
        Subscription subscription
        PropagatedContext contextCapturedOnSubscribe
        List<PropagatedContext> contextsCapturedOnNext = []
        PropagatedContext contextCapturedOnError
        PropagatedContext contextCapturedOnComplete

        @Override
        void onSubscribe(Subscription subscription) {
            this.subscription = subscription
            contextCapturedOnSubscribe = PropagatedContext.getOrEmpty()
        }

        @Override
        void onNext(T t) {
            contextsCapturedOnNext.add(PropagatedContext.getOrEmpty())
        }

        @Override
        void onError(Throwable throwable) {
            contextCapturedOnError = PropagatedContext.getOrEmpty()
        }

        @Override
        void onComplete() {
            contextCapturedOnComplete = PropagatedContext.getOrEmpty()
        }
    }

    static class CorePublisherAdapter<T> implements CorePublisher<T> {
        private final Publisher<T> delegate

        CorePublisherAdapter(Publisher<T> delegate) {
            this.delegate = delegate
        }

        @Override
        void subscribe(CoreSubscriber<? super T> subscriber) {
            delegate.subscribe(subscriber)
        }

        @Override
        void subscribe(Subscriber<? super T> s) {
            delegate.subscribe(s)
        }
    }

    static class CoreSubscriberAdapter<T> implements CoreSubscriber<T> {
        private final Subscriber<T> delegate

        CoreSubscriberAdapter(Subscriber<T> delegate) {
            this.delegate = delegate
        }

        @Override
        void onSubscribe(Subscription s) {
            delegate.onSubscribe(s)
        }

        @Override
        void onNext(T t) {
            delegate.onNext(t)
        }

        @Override
        void onError(Throwable t) {
            delegate.onError(t)
        }

        @Override
        void onComplete() {
            delegate.onComplete()
        }
    }

    static class EmptySubscriber<T> implements Subscriber<T> {

        @Override
        void onSubscribe(Subscription subscription) {
        }

        @Override
        void onNext(T t) {
        }

        @Override
        void onError(Throwable throwable) {
        }

        @Override
        void onComplete() {
        }
    }

    static class PropagatedElement implements PropagatedContextElement {
    }
}
