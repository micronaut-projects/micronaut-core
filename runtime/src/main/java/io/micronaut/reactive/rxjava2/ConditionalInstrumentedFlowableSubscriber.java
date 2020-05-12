package io.micronaut.reactive.rxjava2;

import io.reactivex.FlowableSubscriber;

public class ConditionalInstrumentedFlowableSubscriber<T>
    extends ConditionalInstrumentedSubscriber<T> implements FlowableSubscriber<T> {

    public ConditionalInstrumentedFlowableSubscriber(
        FlowableSubscriber<T> subscriber, ConditionalInstrumenter instrumenter) {
        super(subscriber, instrumenter);
    }
}
