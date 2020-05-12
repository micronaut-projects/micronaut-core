package io.micronaut.reactive.rxjava2;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("ReactiveStreamsPublisherImplementation")
public class ConditionalInstrumentedPublisher<T> implements Publisher<T> {

    private final Publisher<T> publisher;
    private final ConditionalInstrumenter instrumenter;

    public ConditionalInstrumentedPublisher(
        Publisher<T> publisher, ConditionalInstrumenter instrumenter) {
        this.publisher = requireNonNull(publisher, "publisher");
        this.instrumenter = requireNonNull(instrumenter, "instrumenter");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        instrumenter.run(
            () ->
                publisher.subscribe(ConditionalInstrumentedSubscriber.wrap(subscriber, instrumenter)));
    }
}
