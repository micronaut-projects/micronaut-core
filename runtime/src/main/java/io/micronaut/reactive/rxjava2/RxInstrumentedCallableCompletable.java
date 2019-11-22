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
import io.reactivex.CompletableSource;

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
    private final RxInstrumenterFactory instrumenterFactory;
    private final RxInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     */
    RxInstrumentedCallableCompletable(CompletableSource source, RxInstrumenterFactory instrumenterFactory) {
        this.source = source;
        this.instrumenterFactory = instrumenterFactory;
        this.instrumenter = instrumenterFactory.create();
    }

    @Override
    protected void subscribeActual(CompletableObserver s) {
        if (instrumenter != null) {
            CompletableObserver wrap = RxInstrumentedWrappers.wrap(s, instrumenterFactory);
            instrumenter.subscribe(source, wrap);
        } else {
            source.subscribe(s);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T call() throws Exception {
        return ((Callable<T>) source).call();
    }
}
