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
import io.reactivex.Observer;
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
final class RxInstrumentedObserver<T> implements Observer<T>, Disposable, RxInstrumentedComponent {
    protected boolean done;
    private Disposable upstream;
    private final Observer<T> downstream;
    private final RxInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param downstream   The downstream observer
     * @param instrumenter The instrumenter
     */
    RxInstrumentedObserver(Observer<T> downstream, RxInstrumenter instrumenter) {
        this.downstream = downstream;
        this.instrumenter = instrumenter;
    }

    @Override
    public void onSubscribe(Disposable d) {
        if (!validate(upstream, d)) {
            return;
        }
        upstream = d;

        // Operators need to detect the fuseable feature of their immediate upstream. We pass "this"
        // to ensure downstream don't interface with the wrong operator (s).
        downstream.onSubscribe(this);
    }

    @Override
    public void onNext(T t) {
        instrumenter.onNext(downstream, t);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onError(Throwable t) {
        if (done) {
            onStateError(t);
            return;
        }
        done = true;
        instrumenter.onError(downstream, t);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        instrumenter.onComplete(downstream);
    }

    @Override
    public void dispose() {
        upstream.dispose();
    }

    @Override
    public boolean isDisposed() {
        return upstream.isDisposed();
    }
}
