package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
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
abstract class InstrumentedSubscriber<T> implements Subscriber<T>, InstrumentedComponent {
    protected boolean done;
    Subscription upstream;
    private final Subscriber<T> downstream;
    private final List<RunnableInstrumenter> instrumentations;

    /**
     * Default constructor.
     * @param downstream The downstream subscriber
     * @param instrumentations The instrumentations
     */
    InstrumentedSubscriber(
            Subscriber<T> downstream, Collection<ReactiveInstrumenter> instrumentations) {
        ArgumentUtils.requireNonNull("downstream", downstream);
        this.downstream = downstream;
        this.instrumentations = toRunnableInstrumenters(instrumentations);
    }

    /**
     * Default constructor.
     * @param downstream The downstream subscriber
     * @param instrumentations The instrumentations
     */
    InstrumentedSubscriber(
            Subscriber<T> downstream, List<RunnableInstrumenter> instrumentations) {
        ArgumentUtils.requireNonNull("downstream", downstream);
        this.downstream = downstream;
        this.instrumentations = instrumentations;
    }

    @Override public final void onSubscribe(Subscription s) {
        if (!validate(upstream, s)) {
            return;
        }
        upstream = s;

        // Operators need to detect the fuseable feature of their immediate upstream. We pass "this"
        // to ensure downstream don't interface with the wrong operator (s).
        downstream.onSubscribe(upstream);
    }

    @Override
    public void onNext(T t) {
        Runnable onNextRunnable = () -> downstream.onNext(t);
        for (RunnableInstrumenter instrumentation : instrumentations) {
            onNextRunnable = instrumentation.instrument(onNextRunnable);
        }
        onNextRunnable.run();
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onError(Throwable t) {
        if (done) {
            onStateError(t);
            return;
        }
        done = true;

        Runnable onNextRunnable = () -> downstream.onError(t);
        for (RunnableInstrumenter instrumentation : instrumentations) {
            onNextRunnable = instrumentation.instrument(onNextRunnable);
        }
        onNextRunnable.run();
    }

    @Override public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        Runnable onCompleteRunnable = downstream::onComplete;
        for (RunnableInstrumenter instrumentation : instrumentations) {
            onCompleteRunnable = instrumentation.instrument(onCompleteRunnable);
        }
        onCompleteRunnable.run();
    }
}
