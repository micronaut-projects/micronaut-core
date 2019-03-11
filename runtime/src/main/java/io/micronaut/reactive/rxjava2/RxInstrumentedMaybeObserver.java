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
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;

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
final class RxInstrumentedMaybeObserver<T> implements MaybeObserver<T>, Disposable, RxInstrumentedComponent {
    private final MaybeObserver<T> downstream;
    private Disposable upstream;
    private final List<RunnableInstrumenter> instrumentations;

    /**
     * Default constructor.
     * @param downstream The downstream observer
     * @param instrumentations The instrumentations
     */
    RxInstrumentedMaybeObserver(
            MaybeObserver<T> downstream, List<RunnableInstrumenter> instrumentations) {
        this.downstream = downstream;
        this.instrumentations = instrumentations;
    }

    /**
     * Default constructor.
     * @param downstream The downstream observer
     * @param instrumentations The instrumentations
     */
    RxInstrumentedMaybeObserver(
            MaybeObserver<T> downstream, Collection<ReactiveInstrumenter> instrumentations) {
        this.downstream = downstream;
        this.instrumentations = toRunnableInstrumenters(instrumentations);
    }

    @Override public void onSubscribe(Disposable d) {
        if (!validate(upstream, d)) {
            return;
        }
        upstream = d;
        downstream.onSubscribe(this);
    }

    @Override public void onError(Throwable t) {
        Runnable onError = () -> downstream.onError(t);
        for (RunnableInstrumenter instrumentation : instrumentations) {
            onError = instrumentation.instrument(onError);
        }
        onError.run();
    }

    @Override public void onSuccess(T value) {
        Runnable onSuccess = () -> downstream.onSuccess(value);
        for (RunnableInstrumenter instrumentation : instrumentations) {
            onSuccess = instrumentation.instrument(onSuccess);
        }
        onSuccess.run();
    }

    @Override public void onComplete() {
        Runnable onComplete = downstream::onComplete;
        for (RunnableInstrumenter instrumentation : instrumentations) {
            onComplete = instrumentation.instrument(onComplete);
        }
        onComplete.run();
    }

    @Override public boolean isDisposed() {
        return upstream.isDisposed();
    }

    @Override public void dispose() {
        upstream.dispose();
    }
}
