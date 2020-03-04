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
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.CompletableSource;

/**
 * Inspired by code in Brave. Provides general instrumentation abstraction for RxJava2.
 * See https://github.com/openzipkin/brave/tree/master/context/rxjava2/src/main/java/brave/context/rxjava2/internal.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class RxInstrumentedCompletable extends Completable implements RxInstrumentedComponent {
    private final CompletableSource source;
    private final InvocationInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param source       The source
     * @param instrumenter The instrumenter
     */
    RxInstrumentedCompletable(CompletableSource source, InvocationInstrumenter instrumenter) {
        this.source = source;
        this.instrumenter = instrumenter;
    }

    @Override
    protected void subscribeActual(CompletableObserver o) {
        try {
            instrumenter.beforeInvocation();
            source.subscribe(o);
        } finally {
            instrumenter.afterInvocation(false);
        }
    }
}
