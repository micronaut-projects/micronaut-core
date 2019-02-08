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
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import io.reactivex.*;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Collection;
import java.util.List;
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
     * @param downstream The downstream subscriber
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> Subscriber<T> wrap(
            Subscriber<T> downstream, List<RunnableInstrumenter> instrumentations) {
        if (downstream instanceof FlowableSubscriber) {
            return new RxInstrumentedFlowableSubscriber<>(downstream, instrumentations);
        }
        return new RxInstrumentedSubscriber<>(downstream, instrumentations);
    }

    /**
     * Wrap a completable source.
     * @param source The source
     * @param instrumentations The instrumentations
     * @return The wrapped subscriber
     */
    static Completable wrap(
            CompletableSource source, Collection<ReactiveInstrumenter> instrumentations) {
        if (source instanceof Callable) {
            return new RxInstrumentedCallableCompletable<>(source, instrumentations);
        }
        return new RxInstrumentedCompletable(source, instrumentations);
    }

    /**
     * Wrap a maybe.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> Maybe<T> wrap(
            MaybeSource<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        if (source instanceof Callable) {
            return new RxInstrumentedCallableMaybe<>(source, instrumentations);
        }
        return new RxInstrumentedMaybe<>(source, instrumentations);
    }

    /**
     * Wrap a single.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> Single<T> wrap(
            SingleSource<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        if (source instanceof Callable) {
            return new RxInstrumentedCallableSingle<>(source, instrumentations);
        }
        return new RxInstrumentedSingle<>(source, instrumentations);
    }

    /**
     * Wrap a observable.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> Observable<T> wrap(
            ObservableSource<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        if (source instanceof Callable) {
            return new RxInstrumentedCallableObservable<>(source, instrumentations);
        }
        return new RxInstrumentedObservable<>(source, instrumentations);
    }

    /**
     * Wrap a connectable observable.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> ConnectableObservable<T> wrap(
            ConnectableObservable<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        return new RxInstrumentedConnectableObservable<>(source, instrumentations);
    }

    /**
     * Wrap a flowable.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> Flowable<T> wrap(
            Publisher<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        if (source instanceof Callable) {
            return new RxInstrumentedCallableFlowable<>(source, instrumentations);
        }
        return new RxInstrumentedFlowable<>(source, instrumentations);
    }

    /**
     * Wrap a flowable.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */

    static <T> ConnectableFlowable<T> wrap(
            ConnectableFlowable<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        return new RxInstrumentedConnectableFlowable<>(source, instrumentations);
    }

    /**
     * Wrap a parallel flowable.
     * @param source The source
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */

    static <T> ParallelFlowable<T> wrap(
            ParallelFlowable<T> source, Collection<ReactiveInstrumenter> instrumentations) {
        return new RxInstrumentedParallelFlowable<>(source, instrumentations);
    }

    /**
     * Wrap a observer.
     * @param downstream The downstream observer
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> Observer<T> wrap(Observer<T> downstream,
                                List<RunnableInstrumenter> instrumentations) {
        return new RxInstrumentedObserver<>(downstream, instrumentations);
    }

    /**
     * Wrap a observer.
     * @param downstream The downstream observer
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> SingleObserver<T> wrap(SingleObserver<T> downstream,
                                      List<RunnableInstrumenter> instrumentations) {
        return new RxInstrumentedSingleObserver<>(downstream, instrumentations);
    }

    /**
     * Wrap a observer.
     * @param downstream The downstream observer
     * @param instrumentations The instrumentations
     * @param <T> The type
     * @return The wrapped subscriber
     */
    static <T> MaybeObserver<T> wrap(MaybeObserver<T> downstream,
                                     List<RunnableInstrumenter> instrumentations) {
        return new RxInstrumentedMaybeObserver<>(downstream, instrumentations);
    }

    /**
     * Wrap a observer.
     * @param downstream The downstream observer
     * @param instrumentations The instrumentations
     * @return The wrapped subscriber
     */
    static CompletableObserver wrap(CompletableObserver downstream,
                                    List<RunnableInstrumenter> instrumentations) {
        return new RxInstrumentedCompletableObserver(downstream, instrumentations);
    }

}
