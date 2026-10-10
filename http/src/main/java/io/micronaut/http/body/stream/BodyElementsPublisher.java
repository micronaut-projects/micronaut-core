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
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link BodyElements} as a publisher, without Reactor: the requested elements that are
 * available at once ({@link BodyElements#poll()}) are emitted in a loop, the others with one
 * {@link BodyElements#next()} at a time. Cancelling closes the elements.
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
    private final @Nullable Consumer<? super T> discard;
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicInteger wip = new AtomicInteger();
    private final AtomicReference<@Nullable Subscriber<? super T>> downstream = new AtomicReference<>();
    private volatile boolean cancelled;
    private volatile boolean reading;
    private volatile boolean done;
    private final AtomicReference<@Nullable Throwable> badRequest = new AtomicReference<>();
    private final AtomicReference<@Nullable Arrival<T>> arrived = new AtomicReference<>();
    // only accessed by the drain
    private boolean closed;

    /**
     * @param elements The elements, which the publisher takes over
     */
    public BodyElementsPublisher(BodyElements<T> elements) {
        this(elements, null);
    }

    /**
     * @param elements The elements, which the publisher takes over
     * @param discard  Releases an element that arrives once the subscription was cancelled, or
     *                 {@code null} to release a {@link ReferenceCounted} one
     */
    public BodyElementsPublisher(BodyElements<T> elements, @Nullable Consumer<? super T> discard) {
        this.elements = elements;
        this.discard = discard;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        if (!subscribed.compareAndSet(false, true)) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    // the refused subscriber is only failed
                }

                @Override
                public void cancel() {
                    // the refused subscriber is only failed
                }
            });
            subscriber.onError(new IllegalStateException("The elements can be subscribed to only once"));
            return;
        }
        downstream.set(subscriber);
        subscriber.onSubscribe(this);
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            // signalled by the drain, never concurrently with an element
            badRequest.compareAndSet(null, new IllegalArgumentException("§3.9: the number of requested elements must be positive: " + n));
            cancel();
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
        cancelled = true;
        // closed by the drain, so that closing never races with starting a read
        drain();
    }

    /**
     * Emit the elements that are requested: those that are available at once without a stage,
     * then one read at a time. Every signal is emitted here, one at a time.
     */
    private void drain() {
        if (wip.getAndIncrement() != 0) {
            return;
        }
        int missed = 1;
        while (true) {
            Subscriber<? super T> subscriber = downstream.get();
            if (cancelled) {
                if (!closed) {
                    closed = true;
                    elements.close();
                    Throwable failure = badRequest.get();
                    if (failure != null && !done && subscriber != null) {
                        done = true;
                        subscriber.onError(failure);
                    }
                }
                Arrival<T> late = arrived.getAndSet(null);
                if (late != null) {
                    // read while the subscription was cancelled: e.g. a reference counted buffer
                    late.element.ifPresent(this::discard);
                }
            } else if (!done && subscriber != null) {
                emit(subscriber);
            }
            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    private void emit(Subscriber<? super T> subscriber) {
        Arrival<T> arrival = arrived.getAndSet(null);
        if (arrival != null) {
            reading = false;
            if (arrival.error != null) {
                done = true;
                Throwable error = arrival.error;
                subscriber.onError(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
                return;
            }
            Optional<T> element = arrival.element;
            if (element.isEmpty()) {
                done = true;
                subscriber.onComplete();
                return;
            }
            produced();
            subscriber.onNext(element.get());
        }
        while (!reading && !cancelled && requested.get() > 0) {
            T available;
            try {
                available = elements.poll();
            } catch (Exception | Error e) {
                done = true;
                subscriber.onError(e);
                return;
            }
            if (available != null) {
                produced();
                subscriber.onNext(available);
                continue;
            }
            reading = true;
            CompletionStage<Optional<T>> read;
            try {
                read = elements.next();
            } catch (Exception | Error e) {
                read = CompletableFuture.failedFuture(e);
            }
            // a read that completes at once is emitted by the loop of the drain
            read.whenComplete((element, error) -> {
                arrived.set(new Arrival<>(Objects.requireNonNullElse(element, Optional.empty()), error));
                drain();
            });
        }
    }

    private void produced() {
        if (requested.get() != Long.MAX_VALUE) {
            requested.decrementAndGet();
        }
    }

    private void discard(T element) {
        Consumer<? super T> d = discard;
        if (d != null) {
            d.accept(element);
        } else if (element instanceof ReferenceCounted counted) {
            counted.release();
        }
    }

    /**
     * The result of a read.
     *
     * @param element The element, or empty at the end
     * @param error   The failure
     * @param <T>     The type of an element
     */
    private record Arrival<T>(Optional<T> element, @Nullable Throwable error) {
    }
}
