package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;

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
final class RxInstrumentedCallableCompletable<T> extends Completable implements Callable<T>, RxInstrumentedComponent {
    private final CompletableSource source;
    private final List<RunnableInstrumenter> instrumentations;

    /**
     * Default constructor.
     * @param source The source
     * @param instrumentations The instrumentations
     */
    RxInstrumentedCallableCompletable(
            CompletableSource source, List<RunnableInstrumenter> instrumentations) {
        this.source = source;
        this.instrumentations = instrumentations;
    }

    /**
     * Default constructor.
     * @param source The source
     * @param instrumentations The instrumentations
     */
    RxInstrumentedCallableCompletable(
            CompletableSource source, Collection<ReactiveInstrumenter> instrumentations) {
        this.source = source;
        this.instrumentations = toRunnableInstrumenters(instrumentations);
    }

    @Override protected void subscribeActual(CompletableObserver s) {
        source.subscribe(RxInstrumentedWrappers.wrap(s, instrumentations));
    }

    @Override @SuppressWarnings("unchecked") public T call() throws Exception {
        return ((Callable<T>) source).call();
    }
}
