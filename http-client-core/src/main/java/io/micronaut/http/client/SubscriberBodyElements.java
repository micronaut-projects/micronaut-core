/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.stream.PulledBodyElements;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * {@link BodyElements} over a publisher of the elements: one request per read, so nothing is
 * requested before it is asked for.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class SubscriberBodyElements<T> extends PulledBodyElements<T> implements Subscriber<T> {

    private final @Nullable Supplier<? extends Publisher<? extends T>> source;
    private final Runnable discard;
    /**
     * Completes once the first element, the end or a failure arrived, for elements subscribed at
     * once; {@code null} for elements subscribed on the first read.
     */
    private final @Nullable CompletableFuture<BodyElements<T>> started;

    // guarded by this
    private boolean subscribed;
    private @Nullable Subscription subscription;
    private boolean requested;
    private boolean done;

    private SubscriberBodyElements(@Nullable Supplier<? extends Publisher<? extends T>> source,
                                   Runnable discard,
                                   @Nullable CompletableFuture<BodyElements<T>> started) {
        this.source = source;
        this.discard = discard;
        this.started = started;
    }

    /**
     * Elements that subscribe to their publisher on the first read.
     *
     * @param source  Creates the publisher of the elements, on the first read
     * @param discard Discards the source when the elements are closed before they were read
     * @param <T>     The type of an element
     * @return The elements
     */
    public static <T> BodyElements<T> of(Supplier<? extends Publisher<? extends T>> source, Runnable discard) {
        return new SubscriberBodyElements<>(source, discard, null);
    }

    /**
     * Elements that subscribe to their publisher at once, for a publisher whose first signal
     * stands for the response: the stage completes once the first element or the end arrived,
     * with the elements, or fails with the failure of the publisher. Cancelling the stage before
     * cancels the subscription.
     *
     * @param publisher The publisher of the elements
     * @param <T>       The type of an element
     * @return Completes with the elements
     */
    public static <T> CompletableFuture<BodyElements<T>> subscribe(Publisher<? extends T> publisher) {
        CompletableFuture<BodyElements<T>> started = new CompletableFuture<>();
        SubscriberBodyElements<T> elements = new SubscriberBodyElements<>(null, () -> { }, started);
        started.whenComplete((ignored, error) -> {
            if (error instanceof CancellationException) {
                // cancels the subscription
                elements.close();
            }
        });
        synchronized (elements) {
            elements.subscribed = true;
            elements.requested = true;
        }
        publisher.subscribe(elements);
        return started;
    }

    @Override
    protected void demand() {
        boolean subscribe = false;
        Subscription s = null;
        synchronized (this) {
            if (done || requested) {
                return;
            }
            requested = true;
            if (subscribed) {
                // null until onSubscribe, which requests the first element
                s = subscription;
            } else {
                subscribed = true;
                subscribe = true;
            }
        }
        if (subscribe) {
            Publisher<? extends T> publisher;
            try {
                publisher = source == null ? null : source.get();
            } catch (Throwable e) {
                discard.run();
                onError(e);
                return;
            }
            if (publisher == null) {
                onError(new IllegalStateException("No publisher of the elements"));
                return;
            }
            publisher.subscribe(this);
        } else if (s != null) {
            s.request(1);
        }
    }

    @Override
    protected void release() {
        Subscription s;
        boolean neverSubscribed;
        synchronized (this) {
            s = done ? null : subscription;
            neverSubscribed = !subscribed;
            done = true;
            subscribed = true;
        }
        if (s != null) {
            s.cancel();
        } else if (neverSubscribed) {
            discard.run();
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean cancel;
        synchronized (this) {
            subscription = s;
            cancel = done;
        }
        if (cancel) {
            s.cancel();
        } else {
            // the read, or the start, that subscribed
            s.request(1);
        }
    }

    @Override
    public void onNext(T element) {
        synchronized (this) {
            requested = false;
        }
        push(element);
        if (started != null) {
            started.complete(this);
        }
    }

    @Override
    public void onError(Throwable t) {
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
        }
        if (started == null || !started.completeExceptionally(t)) {
            fail(t);
        }
    }

    @Override
    public void onComplete() {
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
        }
        end();
        if (started != null) {
            started.complete(this);
        }
    }
}
