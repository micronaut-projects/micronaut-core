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
package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.multipart.ReleasingFieldPublisher;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A publisher for a single subscriber that buffers the items it is given until they are
 * requested, without a bound. Items are offered by one producer at a time, while the subscriber
 * may request and cancel from any thread. The termination is delivered once the buffered items
 * were delivered. The items that are buffered when the subscriber cancels, and those offered
 * afterwards, are discarded. A request for no item (or a negative number) fails the subscriber
 * with an {@link IllegalArgumentException} and cancels the subscription, as rule 3.9 of the
 * reactive streams specification requires.
 *
 * @param <T> The type of the items
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class UnicastFieldPublisher<T> implements ReleasingFieldPublisher<T>, Subscription {
    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger wip = new AtomicInteger();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final Consumer<? super T> discard;
    private final Runnable onSubscribe;
    private final Runnable onCancel;

    private final AtomicReference<@Nullable Subscriber<? super T>> subscriber = new AtomicReference<>();
    private volatile boolean subscriptionDelivered;
    private volatile boolean done;
    @Nullable
    private Throwable error;
    /**
     * The failure of a request for no item, delivered instead of the items, see rule 3.9 of the
     * reactive streams specification.
     */
    private final AtomicReference<@Nullable IllegalArgumentException> invalidRequest = new AtomicReference<>();
    private volatile boolean cancelled;

    /**
     * @param discard     Releases an item that is not delivered
     * @param onSubscribe Called when the subscriber subscribes, before it is given the
     *                    subscription
     * @param onCancel    Called whenever the subscriber cancels, before the buffered items are
     *                    discarded
     */
    UnicastFieldPublisher(Consumer<? super T> discard, Runnable onSubscribe, Runnable onCancel) {
        this.discard = discard;
        this.onSubscribe = onSubscribe;
        this.onCancel = onCancel;
    }

    /**
     * Offer an item.
     *
     * @param item The item
     * @return {@code false} if the publisher terminated or was cancelled: the item is not taken,
     * and the caller has to release it
     */
    boolean offer(T item) {
        if (done || cancelled) {
            return false;
        }
        queue.offer(item);
        drain(item);
        return true;
    }

    /**
     * Terminate with an error, once the buffered items were delivered.
     *
     * @param e The error
     * @return {@code false} if the publisher already terminated or was cancelled
     */
    boolean error(Throwable e) {
        if (done || cancelled) {
            return false;
        }
        error = e;
        done = true;
        drain(null);
        return true;
    }

    /**
     * Complete, once the buffered items were delivered.
     */
    void complete() {
        if (done || cancelled) {
            return;
        }
        done = true;
        drain(null);
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        if (!subscribed.compareAndSet(false, true)) {
            s.onSubscribe(EmptySubscription.INSTANCE);
            s.onError(new IllegalStateException("UnicastFieldPublisher allows only a single Subscriber"));
            return;
        }
        subscriber.set(s);
        try {
            onSubscribe.run();
        } catch (Throwable e) {
            cancelled = true;
            discardAll();
            s.onSubscribe(EmptySubscription.INSTANCE);
            s.onError(e);
            return;
        }
        s.onSubscribe(this);
        subscriptionDelivered = true;
        if (!cancelled) {
            drain(null);
        }
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            // rule 3.9: the subscriber is failed, and the subscription is cancelled
            if (invalidRequest.get() == null) {
                invalidRequest.set(new IllegalArgumentException("Rule 3.9 of the reactive streams specification: the number of requested items must be positive, but was " + n));
            }
            drain(null);
            return;
        }
        requested.getAndUpdate(old -> {
            long next = old + n;
            return next < 0 ? Long.MAX_VALUE : next;
        });
        drain(null);
    }

    @Override
    public void cancel() {
        onCancel.run();
        if (cancelled) {
            return;
        }
        cancelled = true;
        if (wip.getAndIncrement() == 0) {
            discardAll();
        }
    }

    private void drain(@Nullable T offered) {
        if (wip.getAndIncrement() != 0) {
            if (offered != null && cancelled && queue.remove(offered)) {
                // cancelled concurrently: the drain that holds the loop may have missed the item
                discard.accept(offered);
            }
            return;
        }
        int missed = 1;
        while (true) {
            Subscriber<? super T> s = subscriber.get();
            if (subscriptionDelivered && s != null) {
                drainTo(s);
                return;
            }
            if (cancelled) {
                discardAll();
            }
            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    private void drainTo(Subscriber<? super T> s) {
        int missed = 1;
        while (true) {
            long r = requested.get();
            long e = 0;
            while (e != r) {
                boolean d = done;
                T item = queue.poll();
                if (checkTerminated(d, item == null, s, item)) {
                    // the loop stays taken: nothing is delivered anymore
                    return;
                }
                if (item == null) {
                    break;
                }
                s.onNext(item);
                e++;
            }
            if (e == r && checkTerminated(done, queue.isEmpty(), s, null)) {
                return;
            }
            if (e != 0 && r != Long.MAX_VALUE) {
                requested.addAndGet(-e);
            }
            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    private boolean checkTerminated(boolean d, boolean empty, Subscriber<? super T> s, @Nullable T item) {
        if (cancelled) {
            if (item != null) {
                discard.accept(item);
            }
            discardAll();
            return true;
        }
        IllegalArgumentException invalid = invalidRequest.get();
        if (invalid != null) {
            // like a cancellation of the subscriber, which is then failed
            cancelled = true;
            onCancel.run();
            if (item != null) {
                discard.accept(item);
            }
            discardAll();
            s.onError(invalid);
            return true;
        }
        if (d && empty) {
            Throwable e = error;
            if (e != null) {
                s.onError(e);
            } else {
                s.onComplete();
            }
            return true;
        }
        return false;
    }

    private void discardAll() {
        T item;
        while ((item = queue.poll()) != null) {
            discard.accept(item);
        }
    }

    /**
     * The subscription of a subscriber that is refused.
     */
    private enum EmptySubscription implements Subscription {
        INSTANCE;

        @Override
        public void request(long n) {
            // the subscription of a refused subscriber delivers nothing
        }

        @Override
        public void cancel() {
            // the subscription of a refused subscriber delivers nothing
        }
    }
}
