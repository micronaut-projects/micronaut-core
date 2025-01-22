/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.async.propagation;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

/**
 * Reactive propagation of {@link PropagatedContext}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public final class ReactivePropagation {

    private ReactivePropagation() {
    }

    /**
     * Creates propagation context aware {@link Publisher}.
     *
     * @param propagatedContext The context
     * @param actual The publisher
     * @param <T> The publisher element type
     * @return propagation aware publisher
     */
    public static <T> Publisher<T> propagate(PropagatedContext propagatedContext, Publisher<T> actual) {
        if (actual instanceof CorePublisher<T> corePublisher) {
            return propagate(propagatedContext, corePublisher);
        }
        return subscriber -> {
            try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                actual.subscribe(propagate(propagatedContext, subscriber));
            }
        };
    }
    /**
     * Creates propagation context aware {@link Publisher}.
     *
     * @param propagatedContext The context
     * @param actual The publisher
     * @param <T> The publisher element type
     * @return propagation aware publisher
     * @since 4.8
     */
    public static <T> Publisher<T> propagate(PropagatedContext propagatedContext, CorePublisher<T> actual) {
        return new CorePublisher<>() {
            @Override
            public void subscribe(@NonNull CoreSubscriber<? super T> subscriber) {
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    actual.subscribe(propagate(propagatedContext, subscriber));
                }
            }

            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                if (subscriber instanceof CoreSubscriber<? super T> coreSubscriber) {
                    subscribe(coreSubscriber);
                    return;
                }
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    actual.subscribe(propagate(propagatedContext, subscriber));
                }
            }
        };
    }

    /**
     * Creates propagation context aware {@link Subscriber}.
     *
     * @param propagatedContext The context
     * @param actual The subscriber
     * @param <T> The subscriber element type
     * @return propagation aware subscriber
     */
    public static <T> Subscriber<T> propagate(PropagatedContext propagatedContext, Subscriber<T> actual) {
        return new CoreSubscriber<>() {

            @NonNull
            @Override
            public Context currentContext() {
                Context ctx;
                if (actual instanceof CoreSubscriber<T> actualSubscriber) {
                    ctx = actualSubscriber.currentContext();
                } else {
                    ctx = Context.empty();
                }
                return ReactorPropagation.addPropagatedContext(ctx, propagatedContext);
            }

            @Override
            public void onSubscribe(@NonNull Subscription s) {
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    actual.onSubscribe(s);
                }
            }

            @Override
            public void onNext(T t) {
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    actual.onNext(t);
                }
            }

            @Override
            public void onError(Throwable t) {
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    actual.onError(t);
                }
            }

            @Override
            public void onComplete() {
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    actual.onComplete();
                }
            }
        };
    }

}
