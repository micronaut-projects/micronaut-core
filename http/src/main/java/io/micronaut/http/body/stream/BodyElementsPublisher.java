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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link BodyElements} as a publisher, without Reactor: one {@link BodyElements#next()} per
 * requested element, one at a time. Cancelling closes the elements.
 *
 * <p>One subscriber.</p>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class BodyElementsPublisher<T> implements Publisher<T>, Subscription {

    private final BodyElements<T> elements;
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicInteger wip = new AtomicInteger();
    private volatile @Nullable Subscriber<? super T> downstream;
    private volatile boolean cancelled;
    private volatile boolean reading;
    private volatile boolean done;

    /**
     * @param elements The elements, which the publisher takes over
     */
    public BodyElementsPublisher(BodyElements<T> elements) {
        this.elements = elements;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if (!subscribed.compareAndSet(false, true)) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscriber.onError(new IllegalStateException("The elements can be subscribed to only once"));
            return;
        }
        downstream = subscriber;
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            cancel();
            Subscriber<? super T> subscriber = downstream;
            if (subscriber != null) {
                subscriber.onError(new IllegalArgumentException("§3.9: the number of requested elements must be positive: " + n));
            }
            return;
        }
        long current;
        long next;
        do {
            current = requested.get();
            if (current == Long.MAX_VALUE) {
                break;
            }
            next = current + n < 0 ? Long.MAX_VALUE : current + n;
        } while (!requested.compareAndSet(current, next));
        drain();
    }

    @Override
    public void cancel() {
        if (!cancelled) {
            cancelled = true;
            elements.close();
        }
    }

    private void drain() {
        if (wip.getAndIncrement() != 0) {
            return;
        }
        int missed = 1;
        while (true) {
            if (!cancelled && !done && !reading && requested.get() > 0) {
                reading = true;
                elements.next().whenComplete(this::onRead);
            }
            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    private void onRead(@Nullable Optional<T> element, @Nullable Throwable error) {
        Subscriber<? super T> subscriber = downstream;
        if (cancelled || subscriber == null) {
            return;
        }
        if (error != null) {
            done = true;
            subscriber.onError(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            return;
        }
        if (element == null || element.isEmpty()) {
            done = true;
            subscriber.onComplete();
            return;
        }
        if (requested.get() != Long.MAX_VALUE) {
            requested.decrementAndGet();
        }
        subscriber.onNext(element.get());
        reading = false;
        drain();
    }
}
