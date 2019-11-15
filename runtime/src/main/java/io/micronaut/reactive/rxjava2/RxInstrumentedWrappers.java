/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Subscriber;

import java.util.concurrent.Callable;

/**
 * Inspired by code in Brave. Provides general instrumentation abstraction for RxJava2.
 * See https://github.com/openzipkin/brave/tree/master/context/rxjava2/src/main/java/brave/context/rxjava2/internal.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class RxInstrumentedWrappers {

    /**
     * Wrap a subscriber.
     *
     * @param downstream          The downstream subscriber
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> Subscriber<T> wrap(Subscriber<T> downstream, RxInstrumenterFactory instrumenterFactory) {
        if (downstream instanceof InstrumentedComponent) {
            return downstream;
        }
        if (downstream instanceof FlowableSubscriber) {
            return instrumenterFactory.create()
                    .<Subscriber<T>>map(instrumenter -> new RxInstrumentedFlowableSubscriber<>(downstream, instrumenter))
                    .orElse(downstream);
        }
        return instrumenterFactory.create()
                .<Subscriber<T>>map(instumenter -> new RxInstrumentedSubscriber<T>(downstream, instumenter))
                .orElse(downstream);
    }

    /**
     * Wrap a completable source.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @return The wrapped subscriber
     */
    static Completable wrap(Completable source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        if (source instanceof Callable) {
            return new RxInstrumentedCallableCompletable<>(source, instrumenterFactory);
        }
        return new RxInstrumentedCompletable(source, instrumenterFactory);
    }

    /**
     * Wrap a maybe.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> Maybe<T> wrap(Maybe<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        if (source instanceof Callable) {
            return new RxInstrumentedCallableMaybe<>(source, instrumenterFactory);
        }
        return new RxInstrumentedMaybe<>(source, instrumenterFactory);
    }

    /**
     * Wrap a single.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> Single<T> wrap(Single<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        if (source instanceof Callable) {
            return new RxInstrumentedCallableSingle<>(source, instrumenterFactory);
        }
        return new RxInstrumentedSingle<>(source, instrumenterFactory);
    }

    /**
     * Wrap a observable.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> Observable<T> wrap(Observable<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        if (source instanceof Callable) {
            return new RxInstrumentedCallableObservable<>(source, instrumenterFactory);
        }
        return new RxInstrumentedObservable<>(source, instrumenterFactory);
    }

    /**
     * Wrap a connectable observable.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> ConnectableObservable<T> wrap(ConnectableObservable<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        return new RxInstrumentedConnectableObservable<>(source, instrumenterFactory);
    }

    /**
     * Wrap a flowable.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> Flowable<T> wrap(Flowable<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        if (source instanceof Callable) {
            return new RxInstrumentedCallableFlowable<>(source, instrumenterFactory);
        }
        return new RxInstrumentedFlowable<>(source, instrumenterFactory);
    }

    /**
     * Wrap a flowable.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */

    static <T> ConnectableFlowable<T> wrap(ConnectableFlowable<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        return new RxInstrumentedConnectableFlowable<>(source, instrumenterFactory);
    }

    /**
     * Wrap a parallel flowable.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */

    static <T> ParallelFlowable<T> wrap(ParallelFlowable<T> source, RxInstrumenterFactory instrumenterFactory) {
        if (source instanceof InstrumentedComponent) {
            return source;
        }
        return new RxInstrumentedParallelFlowable<>(source, instrumenterFactory);
    }

    /**
     * Wrap a observer.
     *
     * @param downstream          The downstream observer
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> Observer<T> wrap(Observer<T> downstream, RxInstrumenterFactory instrumenterFactory) {
        if (downstream instanceof InstrumentedComponent) {
            return downstream;
        }
        return instrumenterFactory.create()
                .<Observer<T>>map(instrumenter -> new RxInstrumentedObserver<>(downstream, instrumenter))
                .orElse(downstream);
    }

    /**
     * Wrap a observer.
     *
     * @param downstream          The downstream observer
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> SingleObserver<T> wrap(SingleObserver<T> downstream, RxInstrumenterFactory instrumenterFactory) {
        if (downstream instanceof InstrumentedComponent) {
            return downstream;
        }
        return instrumenterFactory.create()
                .<SingleObserver<T>>map(instrumenter -> new RxInstrumentedSingleObserver<>(downstream, instrumenter))
                .orElse(downstream);
    }

    /**
     * Wrap a observer.
     *
     * @param downstream          The downstream observer
     * @param instrumenterFactory The instrumenterFactory
     * @param <T>                 The type
     * @return The wrapped subscriber
     */
    static <T> MaybeObserver<T> wrap(MaybeObserver<T> downstream, RxInstrumenterFactory instrumenterFactory) {
        if (downstream instanceof InstrumentedComponent) {
            return downstream;
        }
        return instrumenterFactory.create()
                .<MaybeObserver<T>>map(instrumenter -> new RxInstrumentedMaybeObserver<>(downstream, instrumenter))
                .orElse(downstream);
    }

    /**
     * Wrap a observer.
     *
     * @param downstream          The downstream observer
     * @param instrumenterFactory The instrumenterFactory
     * @return The wrapped subscriber
     */
    static CompletableObserver wrap(CompletableObserver downstream, RxInstrumenterFactory instrumenterFactory) {
        if (downstream instanceof InstrumentedComponent) {
            return downstream;
        }
        return instrumenterFactory.create()
                .<CompletableObserver>map(instrumenter -> new RxInstrumentedCompletableObserver(downstream, instrumenter))
                .orElse(downstream);
    }

}
