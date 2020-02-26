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
package io.micronaut.core.async.processor;

import io.micronaut.core.async.subscriber.SingleThreadedBufferingSubscriber;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>A Reactive streams {@link org.reactivestreams.Processor} designed to be used within a single thread and manage
 * back pressure state.</p>
 * <p>
 * <p>This processor only supports a single {@link Subscriber}</p>
 *
 * @param <T> The argument type
 * @param <R> The message type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class SingleThreadedBufferingProcessor<R, T> extends SingleThreadedBufferingSubscriber<R> implements Processor<R, T> {
    private final AtomicReference<Subscriber<? super T>> downstreamSubscriber = new AtomicReference<>();

    @Override
    public void subscribe(Subscriber<? super T> downstreamSubscriber) {
        subscribeDownstream(downstreamSubscriber);
    }

    @Override
    protected void doOnComplete() {
        try {
            currentDownstreamSubscriber().ifPresent(Subscriber::onComplete);
        } catch (Exception e) {
            onError(e);
        }
    }

    @Override
    protected void doOnNext(R message) {
        onUpstreamMessage(message);
    }

    @Override
    protected void doOnSubscribe(Subscription subscription) {
        currentDownstreamSubscriber().ifPresent(this::provideDownstreamSubscription);
    }

    @Override
    protected void doOnError(Throwable t) {
        currentDownstreamSubscriber().ifPresent(subscriber ->
            subscriber.onError(t)
        );
    }

    /**
     * @param downstreamSubscriber The downstream subscriber
     */
    protected void subscribeDownstream(Subscriber<? super T> downstreamSubscriber) {
        if (!this.downstreamSubscriber.compareAndSet(null, downstreamSubscriber)) {
            throw new IllegalStateException("Only one subscriber allowed");
        }
        switch (upstreamState) {
            case NO_SUBSCRIBER:
                if (upstreamBuffer.isEmpty()) {
                    upstreamState = BackPressureState.IDLE;
                } else {
                    upstreamState = BackPressureState.BUFFERING;
                }
                break;
            case IDLE:
            case BUFFERING:
            case FLOWING:
                provideDownstreamSubscription(downstreamSubscriber);
            default:
                // no-op
        }
    }

    /**
     * Called when an message is received from the upstream {@link Subscriber}.
     *
     * @param message The message
     */
    protected abstract void onUpstreamMessage(R message);

    /**
     * Resolve the current {@link Subscriber}.
     *
     * @return An {@link Optional} of the subscriber
     */
    protected Optional<Subscriber<? super T>> currentDownstreamSubscriber() {
        return Optional.ofNullable(this.downstreamSubscriber.get());
    }

    /**
     * Resolve the current {@link Subscriber}.
     *
     * @return An {@link Optional} of the subscriber
     * @throws IllegalStateException If no {@link Subscriber} is present
     */
    protected Subscriber<? super T> getDownstreamSubscriber() {
        return Optional.ofNullable(this.downstreamSubscriber.get()).orElseThrow(() -> new IllegalStateException("No subscriber present!"));
    }
}
