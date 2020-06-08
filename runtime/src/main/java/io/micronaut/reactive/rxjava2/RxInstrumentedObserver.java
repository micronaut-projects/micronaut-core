/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.scheduling.instrument.Instrumentation;
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
    private final InvocationInstrumenter onSubscribeInstrumenter;
    private final InvocationInstrumenter onResultInstrumenter;

    /**
     * Default constructor.
     *
     * @param source              The downstream observer
     * @param instrumenterFactory The instrumenterFactory
     */
    RxInstrumentedObserver(Observer<T> source, RxInstrumenterFactory instrumenterFactory) {
        this.source = source;
        this.onSubscribeInstrumenter = RunOnceInvocationInstrumenter.create(instrumenterFactory);
        this.onResultInstrumenter = RunOnceInvocationInstrumenter.create(instrumenterFactory);
    }

    @Override
    public void onSubscribe(Disposable d) {
        try (Instrumentation ignored = onSubscribeInstrumenter.newInstrumentation()) {
            source.onSubscribe(d);
        }
    }

    @Override
    public void onNext(T t) {
        try (Instrumentation ignored = onResultInstrumenter.newInstrumentation()) {
            source.onNext(t);
        }
    }

    @Override
    public void onError(Throwable t) {
        try (Instrumentation ignored = onResultInstrumenter.newInstrumentation()) {
            source.onError(t);
        }
    }

    @Override
    public void onComplete() {
        try (Instrumentation ignored = onResultInstrumenter.newInstrumentation()) {
            source.onComplete();
        }
    }
}
