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
package org.particleframework.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract Reactive streams processor that is able to buffer and process for the purposes of a single {@link Subscriber}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractSingleSubscriberProcessor<T, R> implements Publisher<T>, Subscriber<R> {
    protected final Queue<R> buffer = new LinkedList<>();
    protected final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();
    protected Subscription parentSubscription;
    protected State state = AbstractSingleSubscriberProcessor.State.NO_SUBSCRIBER;
    private long pendingDemand = 0;

    @Override
    public void onComplete() {
        switch (state) {
            case DONE:
                return;
            case NO_SUBSCRIBER:
            case BUFFERING:
                state = AbstractSingleSubscriberProcessor.State.FLOWING;
            default:
                resolveSubscriber().ifPresent(Subscriber::onComplete);
                state = AbstractSingleSubscriberProcessor.State.DONE;
        }
    }


    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if(!this.subscriber.compareAndSet(null, subscriber)) {
            throw new IllegalStateException("Only one subscriber allowed");
        }
        switch (state) {
            case NO_SUBSCRIBER:
                if (buffer.isEmpty()) {
                    state = AbstractSingleSubscriberProcessor.State.IDLE;
                } else {
                    state = AbstractSingleSubscriberProcessor.State.BUFFERING;
                }
                break;
            case IDLE:
            case BUFFERING:
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        processDemand(n);
                        parentSubscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        parentSubscription.cancel();
                    }
                });
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.parentSubscription = subscription;
        switch (state) {
            case NO_SUBSCRIBER:
                if (buffer.isEmpty()) {
                    state = AbstractSingleSubscriberProcessor.State.IDLE;
                } else {
                    state = AbstractSingleSubscriberProcessor.State.BUFFERING;
                }
                break;
            case FLOWING:
            case IDLE:
                Subscriber subscriber = resolveSubscriber().orElseThrow(()->new IllegalStateException("No subscriber!"));
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        processDemand(n);
                        parentSubscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        parentSubscription.cancel();
                    }
                });
                break;

        }

    }

    @Override
    public void onNext(R message) {
        switch (state) {
            case IDLE:
                buffer.add(message);
                state = AbstractSingleSubscriberProcessor.State.BUFFERING;
                break;
            case NO_SUBSCRIBER:
            case BUFFERING:
                buffer.add(message);
                break;
            case DEMANDING:
                publishDownStream(message);
            case FLOWING:
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        state = AbstractSingleSubscriberProcessor.State.DONE;
        buffer.clear();
        if(parentSubscription != null) {
            parentSubscription.cancel();
        }
        resolveSubscriber().ifPresent(subscriber -> subscriber.onError(t));
    }


    protected abstract void publishDownStream(R message);

    protected void processDemand(long demand) {
        switch (state) {
            case BUFFERING:
            case FLOWING:
                if( addDemand(demand) ) {
                    flushBuffer();
                }
                break;

            case DEMANDING:
                addDemand(demand);
                break;

            case IDLE:
                if (addDemand(demand)) {
                    state = AbstractSingleSubscriberProcessor.State.DEMANDING;
                    flushBuffer();
                }
                break;
            default:

        }
    }

    private boolean addDemand(long demand) {

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
            publishDownStream(buffer.remove());
        }
        if (buffer.isEmpty()) {
            if (pendingDemand > 0) {
                if (state == AbstractSingleSubscriberProcessor.State.BUFFERING) {
                    state = AbstractSingleSubscriberProcessor.State.DEMANDING;
                } // otherwise we're flowing
                parentSubscription.request(pendingDemand);
            } else if (state == AbstractSingleSubscriberProcessor.State.BUFFERING) {
                state = AbstractSingleSubscriberProcessor.State.IDLE;
            }
        }
    }

    private void illegalDemand() {
        resolveSubscriber().ifPresent(subscriber -> subscriber.onError(new IllegalArgumentException("Request for 0 or negative elements in violation of Section 3.9 of the Reactive Streams specification")));
        state = AbstractSingleSubscriberProcessor.State.DONE;
    }

    protected Optional<Subscriber<? super T>> resolveSubscriber() {
        return Optional.ofNullable(this.subscriber.get());
    }

    protected enum State {
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
