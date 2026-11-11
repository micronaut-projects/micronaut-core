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
import io.micronaut.core.propagation.PropagatedContext;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CorePublisher;
import reactor.core.CoreSubscriber;
import org.jspecify.annotations.Nullable;
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
            Subscriber<? super T> wrappedSubscriber = propagate(propagatedContext, subscriber);
            if (propagatedContext.isBound()) {
                actual.subscribe(wrappedSubscriber);
            } else {
                propagatedContext.propagate(() -> actual.subscribe(wrappedSubscriber));
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
            public void subscribe(CoreSubscriber<? super T> subscriber) {
                @SuppressWarnings("unchecked")
                CoreSubscriber<? super T> wrappedSubscriber = (CoreSubscriber<? super T>) propagate(propagatedContext, subscriber);
                if (propagatedContext.isBound()) {
                    actual.subscribe(wrappedSubscriber);
                } else {
                    propagatedContext.propagate(() -> actual.subscribe(wrappedSubscriber));
                }
            }

            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                if (subscriber instanceof CoreSubscriber<? super T> coreSubscriber) {
                    subscribe(coreSubscriber);
                    return;
                }
                Subscriber<? super T> wrappedSubscriber = propagate(propagatedContext, subscriber);
                if (propagatedContext.isBound()) {
                    actual.subscribe(wrappedSubscriber);
                } else {
                    propagatedContext.propagate(() -> actual.subscribe(wrappedSubscriber));
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
        return new PropagatingSubscriber<>(propagatedContext, actual);
    }

    /**
     * Delivers every signal with the context bound. A streamed body passes each element through
     * here, so the subscriber binds the context without a capturing lambda: it is itself the
     * {@link Runnable} that delivers the pending signal, which a field holds. Reactive Streams
     * signals are serial (rule 1.3) and a signal delivered while the context is bound does not
     * touch the field, so one field is enough.
     *
     * @param <T> The element type
     */
    private static final class PropagatingSubscriber<T> implements CoreSubscriber<T>, Runnable {

        private static final int ON_SUBSCRIBE = 0;
        private static final int ON_NEXT = 1;
        private static final int ON_ERROR = 2;
        private static final int ON_COMPLETE = 3;

        private final PropagatedContext propagatedContext;
        private final Subscriber<T> actual;
        /**
         * The downstream context with the propagated context added: operators may ask for it for
         * every signal. It is rebuilt when the downstream context changes (for example a
         * {@code LazySendingSubscriber} returns an empty context until its downstream is
         * attached). The pair is one immutable holder, so a racing read sees a consistent one.
         */
        private @Nullable AugmentedContext augmentedContext;
        private int pendingSignal;
        private @Nullable Object pendingValue;

        PropagatingSubscriber(PropagatedContext propagatedContext, Subscriber<T> actual) {
            this.propagatedContext = propagatedContext;
            this.actual = actual;
        }

        @Override
        public Context currentContext() {
            Context downstreamContext;
            if (actual instanceof CoreSubscriber<T> actualSubscriber) {
                downstreamContext = actualSubscriber.currentContext();
            } else {
                downstreamContext = Context.empty();
            }
            AugmentedContext augmented = augmentedContext;
            if (augmented == null || augmented.downstream != downstreamContext) {
                augmented = new AugmentedContext(downstreamContext, ReactorPropagation.addPropagatedContext(downstreamContext, propagatedContext));
                augmentedContext = augmented;
            }
            return augmented.context;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (propagatedContext.isBound()) {
                actual.onSubscribe(s);
            } else {
                deliver(ON_SUBSCRIBE, s);
            }
        }

        @Override
        public void onNext(T t) {
            if (propagatedContext.isBound()) {
                actual.onNext(t);
            } else {
                deliver(ON_NEXT, t);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (propagatedContext.isBound()) {
                actual.onError(t);
            } else {
                deliver(ON_ERROR, t);
            }
        }

        @Override
        public void onComplete() {
            if (propagatedContext.isBound()) {
                actual.onComplete();
            } else {
                deliver(ON_COMPLETE, null);
            }
        }

        private void deliver(int signal, @Nullable Object value) {
            pendingSignal = signal;
            pendingValue = value;
            propagatedContext.propagate(this);
        }

        /**
         * Delivers the pending signal, called with the context bound.
         */
        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            int signal = pendingSignal;
            Object value = pendingValue;
            pendingValue = null;
            switch (signal) {
                case ON_SUBSCRIBE -> actual.onSubscribe((Subscription) value);
                case ON_NEXT -> actual.onNext((T) value);
                case ON_ERROR -> actual.onError((Throwable) value);
                case ON_COMPLETE -> actual.onComplete();
                default -> throw new IllegalStateException("Unknown signal: " + signal);
            }
        }

        /**
         * A downstream context and the same context with the propagated context added.
         *
         * @param downstream The downstream context
         * @param context    The downstream context with the propagated context
         */
        private record AugmentedContext(Context downstream, Context context) {
        }
    }

}
