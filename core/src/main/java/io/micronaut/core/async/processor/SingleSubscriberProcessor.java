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

import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>A {@link Processor} that only allows a single {@link Subscriber}</p>.
 *
 * @param <T> the type of element signaled to the {@link Subscriber}
 * @param <R> the type of element signaled by the {@link org.reactivestreams.Publisher}
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class SingleSubscriberProcessor<T, R> extends CompletionAwareSubscriber<T> implements Processor<T, R> {

    protected static final Subscription EMPTY_SUBSCRIPTION = new Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {

        }
    };
    protected Subscription parentSubscription;
    private final AtomicReference<Subscriber<? super R>> subscriber = new AtomicReference<>();

    @Override
    public final void subscribe(Subscriber<? super R> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber cannot be null");
        if (!this.subscriber.compareAndSet(null, subscriber)) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Only one subscriber allowed"));
        } else {
            doSubscribe(subscriber);
        }
    }

    /**
     * Override to implement {@link org.reactivestreams.Publisher#subscribe(Subscriber)}.
     *
     * @param subscriber The subscriber
     * @see org.reactivestreams.Publisher#subscribe(Subscriber)
     */
    protected abstract void doSubscribe(Subscriber<? super R> subscriber);

    /**
     * Get the current {@link Subscriber}.
     *
     * @return The {@link Subscriber}
     * @throws IllegalStateException if the subscriber is not present
     */
    protected Subscriber<? super R> getSubscriber() {
        Subscriber<? super R> subscriber = this.subscriber.get();
        verifyState(subscriber);
        return subscriber;
    }

    /**
     * Get the current {@link Subscriber}.
     *
     * @return An {@link Optional} subscriber
     */
    protected Optional<Subscriber<? super R>> currentSubscriber() {
        Subscriber<? super R> subscriber = this.subscriber.get();
        return Optional.ofNullable(subscriber);
    }

    /**
     * Called after {@link #doOnError(Throwable)} completes.
     *
     * @param throwable The error
     */
    protected void doAfterOnError(Throwable throwable) {
        // no-op
    }

    /**
     * Called after {@link #doOnComplete()} completes.
     */
    protected void doAfterComplete() {
        // no-op
    }

    /**
     * Called after {@link #doOnSubscribe(Subscription)} completes.
     * @param subscription subscription
     */
    protected void doAfterOnSubscribe(Subscription subscription) {
        // no-op
    }

    /**
     * Perform the actual subscription to the subscriber.
     *
     * @param subscription The subscription
     * @param subscriber   The subscriber (never null)
     */
    protected void doOnSubscribe(Subscription subscription, Subscriber<? super R> subscriber) {
        subscriber.onSubscribe(subscription);
    }

    @Override
    protected final void doOnSubscribe(Subscription subscription) {
        this.parentSubscription = subscription;
        Subscriber<? super R> subscriber = this.subscriber.get();

        if (!verifyState(subscriber)) {
            return;
        }

        doOnSubscribe(subscription, subscriber);
        doAfterOnSubscribe(subscription);
    }

    @Override
    protected final void doOnError(Throwable t) {
        try {
            Subscriber<? super R> subscriber = getSubscriber();
            parentSubscription.cancel();
            subscriber.onError(t);
        } finally {
            doAfterOnError(t);
        }
    }

    @Override
    protected void doOnComplete() {
        try {
            Subscriber<? super R> subscriber = getSubscriber();
            subscriber.onComplete();
        } finally {
            doAfterComplete();
        }
    }

    private boolean verifyState(Subscriber<? super R> subscriber) {
        if (subscriber == null) {
            throw new IllegalStateException("No subscriber present!");
        }

        boolean hasParent = parentSubscription != null;
        if (!hasParent) {
            subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
            subscriber.onError(new IllegalStateException("Upstream publisher must be subscribed to first"));
        }
        return hasParent;
    }
}
