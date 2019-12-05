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
import io.reactivex.disposables.Disposable;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.Consumer;
import org.reactivestreams.Subscriber;


/**
 * Inspired by code in Brave. Provides general instrumentation abstraction for RxJava2.
 * See https://github.com/openzipkin/brave/tree/master/context/rxjava2/src/main/java/brave/context/rxjava2/internal.
 *
 * @param <T> The type
 * @author graemerocher
 * @since 1.1
 */
@Internal
final class RxInstrumentedConnectableFlowable<T> extends ConnectableFlowable<T> implements RxInstrumentedComponent {
    private final ConnectableFlowable<T> source;
    private final InvocationInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param source              The source
     * @param instrumenterFactory The instrumenterFactory
     */
    RxInstrumentedConnectableFlowable(ConnectableFlowable<T> source, RxInstrumenterFactory instrumenterFactory) {
        this.source = source;
        this.instrumenter = instrumenterFactory.create();
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        if (instrumenter != null) {
            try {
                instrumenter.beforeInvocation();
                source.subscribe(s);
            } finally {
                instrumenter.afterInvocation();
            }
        } else {
            source.subscribe(s);
        }
    }

    @Override
    public void connect(Consumer<? super Disposable> connection) {
        if (instrumenter != null) {
            try {
                instrumenter.beforeInvocation();
                source.connect(connection);
            } finally {
                instrumenter.afterInvocation();
            }
        } else {
            source.connect(connection);
        }
    }
}

