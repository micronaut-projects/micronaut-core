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

import io.reactivex.FlowableSubscriber;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper subscriber to instrument each {@link Subscriber} methods with the given {@link ConditionalInstrumenter}.
 * Mainly used in within similar instrumented wrappers i.e. {@link ConditionalInstrumentedPublisher}.
 *
 * @param <T> type of the subscription element
 * @author lgathy
 * @see ConditionalInstrumentedFlowableSubscriber
 * @since 2.0
 */
@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
public class ConditionalInstrumentedSubscriber<T> implements Subscriber<T> {

    /**
     * Factory method to wrap a given {@link Subscriber} with a {@link ConditionalInstrumenter}. If {@code subscriber}
     * implements {@link FlowableSubscriber} the returned wrapped instance will also do so.
     *
     * @param subscriber   The downstream subscriber
     * @param instrumenter The instrumenter
     * @param <T>          The type
     * @return The wrapped subscriber
     */
    public static <T> Subscriber<T> wrap(Subscriber<T> subscriber, ConditionalInstrumenter instrumenter) {
        if (subscriber instanceof FlowableSubscriber) {
            return new ConditionalInstrumentedFlowableSubscriber<>((FlowableSubscriber<T>) subscriber, instrumenter);
        } else {
            return new ConditionalInstrumentedSubscriber<>(subscriber, instrumenter);
        }
    }

    private final Subscriber<T> subscriber;
    private final ConditionalInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param subscriber   The source subscriber
     * @param instrumenter The instrumenter
     */
    public ConditionalInstrumentedSubscriber(Subscriber<T> subscriber, ConditionalInstrumenter instrumenter) {
        this.subscriber = requireNonNull(subscriber, "subscriber");
        this.instrumenter = requireNonNull(instrumenter, "instrumenter");
    }

    @Override
    public final void onSubscribe(Subscription s) {
        instrumenter.run(() -> subscriber.onSubscribe(s));
    }

    @Override
    public void onNext(T t) {
        instrumenter.run(() -> subscriber.onNext(t));
    }

    @Override
    public void onError(Throwable t) {
        instrumenter.run(() -> subscriber.onError(t));
    }

    @Override
    public void onComplete() {
        instrumenter.run(subscriber::onComplete);
    }
}
