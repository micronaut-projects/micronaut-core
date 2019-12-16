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
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
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
final class RxInstrumentedObserver<T> implements Observer<T>, RxInstrumentedComponent {
    private final Observer<T> source;
    private final RxInstrumenterFactory instrumenterFactory;

    /**
     * Default constructor.
     *
     * @param source              The downstream observer
     * @param instrumenterFactory The instrumenterFactory
     */
    RxInstrumentedObserver(Observer<T> source, RxInstrumenterFactory instrumenterFactory) {
        this.source = source;
        this.instrumenterFactory = instrumenterFactory;
    }

    @Override
    public void onSubscribe(Disposable d) {
        InvocationInstrumenter instrumenter = instrumenterFactory.create();
        if (instrumenter == null) {
            source.onSubscribe(d);
        } else {
            try {
                instrumenter.beforeInvocation();
                source.onSubscribe(d);
            } finally {
                instrumenter.afterInvocation();
            }
        }
    }

    @Override
    public void onNext(T t) {
        InvocationInstrumenter instrumenter = instrumenterFactory.create();
        if (instrumenter == null) {
            source.onNext(t);
        } else {
            try {
                instrumenter.beforeInvocation();
                source.onNext(t);
            } finally {
                instrumenter.afterInvocation();
            }
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onError(Throwable t) {
        InvocationInstrumenter instrumenter = instrumenterFactory.create();
        if (instrumenter == null) {
            source.onError(t);
        } else {
            try {
                instrumenter.beforeInvocation();
                source.onError(t);
            } finally {
                instrumenter.afterInvocation();
            }
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void onComplete() {
        InvocationInstrumenter instrumenter = instrumenterFactory.create();
        if (instrumenter == null) {
            source.onComplete();
        } else {
            try {
                instrumenter.beforeInvocation();
                source.onComplete();
            } finally {
                instrumenter.afterInvocation();
            }
        }
    }

}
