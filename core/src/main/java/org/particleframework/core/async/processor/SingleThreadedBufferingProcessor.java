/*
 * Copyright 2017 original authors
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
package org.particleframework.core.async.processor;

import org.particleframework.core.async.subscriber.SingleThreadedBufferingSubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * <p>A Reactive streams {@link org.reactivestreams.Processor} designed to be used within a single thread and manage back pressure state.</p>
 *
 * <p>This processor only supports a single {@link Subscriber}</p>
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class SingleThreadedBufferingProcessor<R, T> extends SingleThreadedBufferingSubscriber<R> implements Publisher<T> {
    private final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();

    @Override
    protected void doOnComplete() {
        currentSubscriber().ifPresent(Subscriber::onComplete);
    }

    @Override
    protected void doOnNext(R message) {
        onMessage(message);
    }

    @Override
    protected void doOnSubscribe(Subscription subscription) {
        currentSubscriber().ifPresent(this::provideSubscription);
    }

    @Override
    protected void doOnError(Throwable t) {
        currentSubscriber().ifPresent(subscriber -> subscriber.onError(t));
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if(!this.subscriber.compareAndSet(null, subscriber)) {
            throw new IllegalStateException("Only one subscriber allowed");
        }
        switch (state) {
            case NO_SUBSCRIBER:
                if (buffer.isEmpty()) {
                    state = BackPressureState.IDLE;
                } else {
                    state = BackPressureState.BUFFERING;
                }
                break;
            case IDLE:
            case BUFFERING:
            case FLOWING:
                provideSubscription(subscriber);
        }
    }


    /**
     * Publish a downstream message
     *
     * @param message The message
     */
    protected abstract void onMessage(R message);

    /**
     * Resolve the current {@link Subscriber}
     *
     * @return An {@link Optional} of the subscriber
     */
    protected Optional<Subscriber<? super T>> currentSubscriber() {
        return Optional.ofNullable(this.subscriber.get());
    }

    /**
     * Resolve the current {@link Subscriber}
     *
     * @return An {@link Optional} of the subscriber
     * @throws IllegalStateException If no {@link Subscriber} is present
     */
    protected Subscriber<? super T> getSubscriber() {
        return Optional.ofNullable(this.subscriber.get()).orElseThrow(()-> new IllegalStateException("No subscriber present!"));
    }
    private void provideSubscription(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new Subscription() {
            @Override
            public synchronized void request(long n) {
                processDemand(n);
                parentSubscription.request(n);
            }

            @Override
            public synchronized void cancel() {
                parentSubscription.cancel();
            }
        });
    }

    private void processDemand(long demand) {
        switch (state) {
            case BUFFERING:
            case FLOWING:
                if( registerDemand(demand) ) {
                    flushBuffer();
                }
                break;

            case DEMANDING:
                registerDemand(demand);
                break;

            case IDLE:
                if (registerDemand(demand)) {
                    state = BackPressureState.DEMANDING;
                    flushBuffer();
                }
                break;
            default:

        }
    }


    private boolean registerDemand(long demand) {

        if (demand <= 0) {
            illegalDemand();
            return false;
        } else {
            if (pendingDemand < Long.MAX_VALUE) {
                pendingDemand += demand;
                if (pendingDemand < 0) {
                    pendingDemand = Long.MAX_VALUE;
                }
            }
            return true;
        }
    }

    private void flushBuffer() {
        while (!buffer.isEmpty() && (pendingDemand > 0 || pendingDemand == Long.MAX_VALUE)) {
            onMessage(buffer.remove());
        }
        if (buffer.isEmpty()) {
            if (pendingDemand > 0) {
                if (state == BackPressureState.BUFFERING) {
                    state = BackPressureState.DEMANDING;
                } // otherwise we're flowing
                parentSubscription.request(pendingDemand);
            } else if (state == BackPressureState.BUFFERING) {
                state = BackPressureState.IDLE;
            }
        }
    }

    private void illegalDemand() {
        currentSubscriber().ifPresent(subscriber -> subscriber.onError(new IllegalArgumentException("Request for 0 or negative elements in violation of Section 3.9 of the Reactive Streams specification")));
        state = BackPressureState.DONE;
    }

}
