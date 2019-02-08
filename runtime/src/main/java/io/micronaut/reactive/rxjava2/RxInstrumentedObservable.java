package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;

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
final class RxInstrumentedObservable<T> extends Observable<T> implements RxInstrumentedComponent {
    private final ObservableSource<T> source;
    private final List<RunnableInstrumenter> instrumentations;

    /**
     * Default constructor.
     * @param source The source
     * @param instrumentations The instrumentations
     */
    RxInstrumentedObservable(
            ObservableSource<T> source, List<RunnableInstrumenter> instrumentations) {
        this.source = source;
        this.instrumentations = instrumentations;
    }

    /**
     * Default constructor.
     * @param source The source
     * @param instrumentations The instrumentations
     */
    RxInstrumentedObservable(
            ObservableSource<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        this.source = source;
        this.instrumentations = toRunnableInstrumenters(instrumentations);
    }

    @Override protected void subscribeActual(Observer<? super T> o) {
        source.subscribe(new RxInstrumentedObserver<>(o, instrumentations));
    }
}
