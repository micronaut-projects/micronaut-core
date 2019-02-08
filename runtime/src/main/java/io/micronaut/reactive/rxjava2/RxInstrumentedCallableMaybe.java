package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Inspired by code in Brave. Provides general instrumentation abstraction for RxJava2.
 * See https://github.com/openzipkin/brave/tree/master/context/rxjava2/src/main/java/brave/context/rxjava2/internal.
 *
 * @param <T> The type
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class RxInstrumentedCallableMaybe<T> extends Maybe<T> implements Callable<T>, RxInstrumentedComponent {
    private final MaybeSource<T> source;
    private final List<RunnableInstrumenter> instrumentations;

    /**
     * Default constructor.
     * @param source The source
     * @param instrumentations The instrumentations
     */
    RxInstrumentedCallableMaybe(
            MaybeSource<T> source, List<RunnableInstrumenter> instrumentations) {
        this.source = source;
        this.instrumentations = instrumentations;
    }

    /**
     * Default constructor.
     * @param source The source
     * @param instrumentations The instrumentations
     */
    RxInstrumentedCallableMaybe(
            MaybeSource<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        this.source = source;
        this.instrumentations = toRunnableInstrumenters(instrumentations);
    }

    @Override protected void subscribeActual(MaybeObserver<? super T> o) {
        source.subscribe(RxInstrumentedWrappers.wrap(o, instrumentations));
    }

    @Override @SuppressWarnings("unchecked") public T call() throws Exception {
        return ((Callable<T>) source).call();
    }
}
