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
package io.micronaut.core.async.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Publisher} that only allows a single {@link Subscriber}.
 *
 * @param <T> the type of element signaled.
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class SingleSubscriberPublisher<T> implements Publisher<T> {
    protected static final Subscription EMPTY_SUBSCRIPTION = new Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {

        }
    };

    private final AtomicReference<Subscriber<? super T>> subscriber = new AtomicReference<>();

    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber cannot be null");
        if (!this.subscriber.compareAndSet(null, subscriber)) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Only one subscriber allowed"));
        } else {
            doSubscribe(subscriber);
        }
    }

    /**
     * Override to implement {@link Publisher#subscribe(Subscriber)}.
     *
     * @param subscriber The subscriber
     * @see Publisher#subscribe(Subscriber)
     */
    protected abstract void doSubscribe(Subscriber<? super T> subscriber);

    /**
     * @return Obtain the current subscriber
     */
    protected Optional<Subscriber<? super T>> currentSubscriber() {
        return Optional.ofNullable(subscriber.get());
    }
}
