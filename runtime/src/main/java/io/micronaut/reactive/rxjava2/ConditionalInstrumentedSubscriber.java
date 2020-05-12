package io.micronaut.reactive.rxjava2;

import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
public class ConditionalInstrumentedSubscriber<T> implements Subscriber<T> {

    public static <T> Subscriber<T> wrap(
        Subscriber<T> subscriber, ConditionalInstrumenter instrumenter) {
        if (subscriber instanceof FlowableSubscriber) {
            return new ConditionalInstrumentedFlowableSubscriber<>(
                (FlowableSubscriber<T>) subscriber, instrumenter);
        } else {
            return new ConditionalInstrumentedSubscriber<>(subscriber, instrumenter);
        }
    }

    private final Subscriber<T> subscriber;
    private final ConditionalInstrumenter instrumenter;

    public ConditionalInstrumentedSubscriber(
        Subscriber<T> subscriber, ConditionalInstrumenter instrumenter) {
        this.subscriber = requireNonNull(subscriber, "subscriber");
        this.instrumenter = requireNonNull(instrumenter, "instrumenter");
    }

    @Override
    public final void onSubscribe(Subscription s) {
        instrumenter.run(() -> subscriber.onSubscribe(s));
    }

    @Override
    public void onNext(T t) {
        instrumenter.run(() -> subscriber.onNext(t));
    }

    @Override
    public void onError(Throwable t) {
        instrumenter.run(() -> subscriber.onError(t));
    }

    @Override
    public void onComplete() {
        instrumenter.run(subscriber::onComplete);
    }
}
