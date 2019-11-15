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
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

/**
 * Inspired by code in Brave. Provides general instrumentation abstraction for RxJava2.
 * See https://github.com/openzipkin/brave/tree/master/context/rxjava2/src/main/java/brave/context/rxjava2/internal.
 *
 * @param <T> The type
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class RxInstrumentedSingleObserver<T> implements SingleObserver<T>, Disposable, RxInstrumentedComponent {
    protected final SingleObserver<T> downstream;
    private final RxInstrumenter instrumenter;
    private Disposable upstream;

    /**
     * Default constructor.
     *
     * @param downstream   The downstream observer
     * @param instrumenter The instrumenter
     */
    RxInstrumentedSingleObserver(SingleObserver<T> downstream, RxInstrumenter instrumenter) {
        this.downstream = downstream;
        this.instrumenter = instrumenter;
    }

    @Override
    public void onSubscribe(Disposable d) {
        if (!validate(upstream, d)) {
            return;
        }
        upstream = d;
        downstream.onSubscribe(this);
    }

    @Override
    public void onError(Throwable t) {
        instrumenter.onError(downstream, t);
    }

    @Override
    public void onSuccess(T value) {
        instrumenter.onSuccess(downstream, value);
    }

    @Override
    public boolean isDisposed() {
        return upstream.isDisposed();
    }

    @Override
    public void dispose() {
        upstream.dispose();
    }
}
