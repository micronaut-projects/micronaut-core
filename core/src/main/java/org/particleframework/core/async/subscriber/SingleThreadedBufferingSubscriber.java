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
package org.particleframework.core.async.subscriber;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A {@link Subscriber} designed to be used by a single thread that buffers incoming data for the purposes of managing back pressure
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class SingleThreadedBufferingSubscriber<T> implements Subscriber<T> {
    protected final Queue<T> buffer = new LinkedList<>();
    protected BackPressureState state = BackPressureState.NO_SUBSCRIBER;
    protected long pendingDemand = 0;
    protected Subscription parentSubscription;

    @Override
    public final void onSubscribe(Subscription subscription) {
        this.parentSubscription = subscription;
        switch (state) {
            case NO_SUBSCRIBER:
                if (buffer.isEmpty()) {
                    state = BackPressureState.IDLE;
                } else {
                    state = BackPressureState.BUFFERING;
                }
                break;
            case FLOWING:
            case IDLE:
                doOnSubscribe(subscription);
                break;

        }

    }

    @Override
    public final void onComplete() {
        switch (state) {
            case DONE:
                return;
            case NO_SUBSCRIBER:
            case BUFFERING:
                state = BackPressureState.FLOWING;
            default:
                doOnComplete();
                state = BackPressureState.DONE;
        }
    }

    @Override
    public final void onNext(T message) {
        switch (state) {
            case IDLE:
                buffer.add(message);
                state = BackPressureState.BUFFERING;
                break;
            case NO_SUBSCRIBER:
            case BUFFERING:
                buffer.add(message);
                break;
            case DEMANDING:
                try {
                    doOnNext(message);
                } finally {
                    if (pendingDemand < Long.MAX_VALUE) {
                        pendingDemand--;
                        if (pendingDemand == 0 && state != BackPressureState.FLOWING) {
                            if (buffer.isEmpty()) {
                                state = BackPressureState.IDLE;
                            } else {
                                state = BackPressureState.BUFFERING;
                            }
                        }
                    }
                }
        }
    }

    @Override
    public final void onError(Throwable t) {
        if (state != BackPressureState.DONE) {
            try {
                if(parentSubscription != null) {
                    parentSubscription.cancel();
                }
            } finally {
                state = BackPressureState.DONE;
                buffer.clear();
                doOnError(t);
            }
        }
    }

    /**
     * Implement {@link Subscriber#onSubscribe(Subscription)}
     */
    protected abstract void doOnSubscribe(Subscription subscription);

    /**
     * Implement {@link Subscriber#onNext(Object)}
     */
    protected abstract void doOnNext(T message);

    /**
     * Implement {@link Subscriber#onError(Throwable)}
     */
    protected abstract void doOnError(Throwable t);

    /**
     * Implement {@link Subscriber#onComplete()}
     */
    protected abstract void doOnComplete();

    protected enum BackPressureState {
        /**
         * There is no subscriber
         */
        NO_SUBSCRIBER,
        /**
         * There is no demand yet and no buffering has taken place
         */
        IDLE,

        /**
         * Buffering has stared, but not demand present
         */
        BUFFERING,

        /**
         * The buffer is empty but there demand
         */
        DEMANDING,

        /**
         * The data has been read, however the buffer is not empty
         */
        FLOWING,

        /**
         * Finished
         */
        DONE
    }
}
