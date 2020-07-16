/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.async.subscriber;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A {@link Subscriber} designed to be used by a single thread that buffers incoming data for the purposes of managing
 * back pressure.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class SingleThreadedBufferingSubscriber<T> implements Subscriber<T>, Emitter<T> {
    protected final Queue<T> upstreamBuffer = new LinkedList<>();
    protected BackPressureState upstreamState = BackPressureState.NO_SUBSCRIBER;
    protected long upstreamDemand = 0;
    protected Subscription upstreamSubscription;

    @Override
    public final synchronized void onSubscribe(Subscription subscription) {
        this.upstreamSubscription = subscription;
        switch (upstreamState) {
            case NO_SUBSCRIBER:
                if (upstreamBuffer.isEmpty()) {
                    upstreamState = BackPressureState.IDLE;
                } else {
                    upstreamState = BackPressureState.BUFFERING;
                }
                break;

            case FLOWING:
            case IDLE:
                doOnSubscribe(subscription);
                break;
            default:
                // no-op
        }
    }

    @Override
    public final void onComplete() {
        switch (upstreamState) {
            case DONE:
                return;

            case NO_SUBSCRIBER:
            case BUFFERING:
                upstreamState = BackPressureState.FLOWING;

            default:
                doOnComplete();
                upstreamState = BackPressureState.DONE;
        }
    }

    @Override
    public final void onNext(T message) {
        switch (upstreamState) {
            case IDLE:
                upstreamBuffer.add(message);
                upstreamState = BackPressureState.BUFFERING;
                break;

            case NO_SUBSCRIBER:
            case BUFFERING:
                upstreamBuffer.add(message);
                break;

            case DEMANDING:
                try {
                    try {
                        doOnNext(message);
                    } catch (Exception e) {
                        onError(e);
                    }
                } finally {
                    if (upstreamState != BackPressureState.DONE && upstreamDemand < Long.MAX_VALUE) {
                        upstreamDemand--;
                        if (upstreamDemand == 0 && upstreamState != BackPressureState.FLOWING) {
                            if (upstreamBuffer.isEmpty()) {
                                upstreamState = BackPressureState.IDLE;
                            } else {
                                upstreamState = BackPressureState.BUFFERING;
                            }
                        }
                    }
                }
            default:
                // no-op
        }
    }

    @Override
    public final void onError(Throwable t) {
        if (upstreamState != BackPressureState.DONE) {
            try {
                if (upstreamSubscription != null) {
                    upstreamSubscription.cancel();
                }
            } finally {
                upstreamState = BackPressureState.DONE;
                upstreamBuffer.clear();
                doOnError(t);
            }
        }
    }

    /**
     * Implement {@link Subscriber#onSubscribe(Subscription)}.
     *
     * @param subscription The subscription
     */
    protected abstract void doOnSubscribe(Subscription subscription);

    /**
     * Implement {@link Subscriber#onNext(Object)}.
     *
     * @param message The message
     */
    protected abstract void doOnNext(T message);

    /**
     * Implement {@link Subscriber#onError(Throwable)}.
     *
     * @param t The throwable
     */
    protected abstract void doOnError(Throwable t);

    /**
     * Implement {@link Subscriber#onComplete()}.
     */
    protected abstract void doOnComplete();

    /**
     * @param subscriber The subscriber
     */
    protected void provideDownstreamSubscription(Subscriber subscriber) {
        subscriber.onSubscribe(newDownstreamSubscription());
    }

    /**
     * @return The subscription
     */
    protected Subscription newDownstreamSubscription() {
        return new DownstreamSubscription();
    }

    private boolean registerDemand(long demand) {
        if (demand <= 0) {
            illegalDemand();
            return false;
        }
        if (upstreamDemand < Long.MAX_VALUE) {
            upstreamDemand += demand;
            if (upstreamDemand < 0) {
                upstreamDemand = Long.MAX_VALUE;
            }
        }
        return true;
    }

    private void flushBuffer() {
        while (!upstreamBuffer.isEmpty() && (upstreamDemand > 0 || upstreamDemand == Long.MAX_VALUE)) {
            onNext(upstreamBuffer.remove());
        }
        if (upstreamBuffer.isEmpty()) {
            if (upstreamDemand > 0) {
                if (upstreamState == BackPressureState.BUFFERING) {
                    upstreamState = BackPressureState.DEMANDING;
                } // otherwise we're flowing
                upstreamSubscription.request(upstreamDemand);
            } else if (upstreamState == BackPressureState.BUFFERING) {
                upstreamState = BackPressureState.IDLE;
            }
        }
    }

    private void illegalDemand() {
        onError(new IllegalArgumentException("Request for 0 or negative elements in violation of Section 3.9 of the Reactive Streams specification"));
    }

    /**
     * Back pressure state.
     */
    protected enum BackPressureState {

        /**
         * There is no subscriber.
         */
        NO_SUBSCRIBER,

        /**
         * There is no demand yet and no buffering has taken place.
         */
        IDLE,

        /**
         * Buffering has stared, but not demand present.
         */
        BUFFERING,

        /**
         * The buffer is empty but there demand.
         */
        DEMANDING,

        /**
         * The data has been read, however the buffer is not empty.
         */
        FLOWING,

        /**
         * Finished.
         */
        DONE
    }

    /**
     * A downstream subscription.
     */
    protected class DownstreamSubscription implements Subscription {
        @Override
        public synchronized void request(long n) {
            processDemand(n);
            upstreamSubscription.request(n);
        }

        @Override
        public synchronized void cancel() {
            upstreamSubscription.cancel();
        }

        private void processDemand(long demand) {
            switch (upstreamState) {
                case BUFFERING:
                case FLOWING:
                    if (registerDemand(demand)) {
                        flushBuffer();
                    }
                    break;

                case DEMANDING:
                    registerDemand(demand);
                    break;

                case IDLE:
                    if (registerDemand(demand)) {
                        upstreamState = BackPressureState.DEMANDING;
                        flushBuffer();
                    }
                    break;
                default:

            }
        }
    }
}
