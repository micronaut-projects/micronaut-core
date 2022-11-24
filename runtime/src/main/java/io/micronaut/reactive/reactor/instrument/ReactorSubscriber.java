/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.reactive.reactor.instrument;

import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

/**
 * An {@link CoreSubscriber} with a support of instrumentation.
 *
 * @param <T> The type
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
final class ReactorSubscriber<T> implements CoreSubscriber<T> {
    private final InvocationInstrumenter instrumenter;
    private final CoreSubscriber<? super T> subscriber;

    public ReactorSubscriber(InvocationInstrumenter instrumenter, CoreSubscriber<? super T> subscriber) {
        this.instrumenter = instrumenter;
        this.subscriber = subscriber;
    }

    @Override
    public Context currentContext() {
        return subscriber.currentContext();
    }

    @Override
    public void onSubscribe(Subscription s) {
        try (Instrumentation ignore = instrumenter.newInstrumentation()) {
            subscriber.onSubscribe(s);
        }
    }

    @Override
    public void onNext(T t) {
        try (Instrumentation ignore = instrumenter.newInstrumentation()) {
            subscriber.onNext(t);
        }
    }

    @Override
    public void onError(Throwable t) {
        try (Instrumentation ignore = instrumenter.newInstrumentation()) {
            subscriber.onError(t);
        }
    }

    @Override
    public void onComplete() {
        try (Instrumentation ignore = instrumenter.newInstrumentation()) {
            subscriber.onComplete();
        }
    }
}
