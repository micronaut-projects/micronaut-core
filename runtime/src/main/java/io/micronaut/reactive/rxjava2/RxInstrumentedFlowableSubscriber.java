package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.List;

/**
 * Inspired by code in Brave. Provides general instrumentation abstraction for RxJava2.
 * See https://github.com/openzipkin/brave/tree/master/context/rxjava2/src/main/java/brave/context/rxjava2/internal.
 *
 * @param <T> The type
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class RxInstrumentedFlowableSubscriber<T> extends RxInstrumentedSubscriber<T>
        implements FlowableSubscriber<T>, Subscription {

    /**
     * Default constructor.
     * @param downstream the downstream subscriber
     * @param instrumentations The instrumentations
     */
    RxInstrumentedFlowableSubscriber(Subscriber<T> downstream, Collection<ReactiveInstrumenter> instrumentations) {
        super(downstream, instrumentations);
    }

    /**
     * Default constructor.
     * @param downstream the downstream subscriber
     * @param instrumentations The instrumentations
     */
    RxInstrumentedFlowableSubscriber(Subscriber<T> downstream, List<RunnableInstrumenter> instrumentations) {
        super(downstream, instrumentations);
    }

    @Override public void request(long n) {
        upstream.request(n);
    }

    @Override public void cancel() {
        upstream.cancel();
    }
}
