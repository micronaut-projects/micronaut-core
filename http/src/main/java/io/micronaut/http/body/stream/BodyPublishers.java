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
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Publishers of the pieces of bodies, without Reactor operators. A publisher that subscribes to
 * a Reactor source passes on the Reactor context of its subscriber, or a context with a discard
 * hook of its own, so that the source releases the items it drops when it is cancelled, see
 * {@link ReactorInterop}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class BodyPublishers {
    private static final Subscription REJECTED = new Subscription() {
        @Override
        public void request(long n) {
            // a rejected subscriber is only failed
        }

        @Override
        public void cancel() {
            // a rejected subscriber is only failed
        }
    };

    private BodyPublishers() {
    }

    /**
     * Map each item of a source.
     *
     * @param source The source
     * @param mapper Maps an item, which it takes over
     * @param <T>    The type of an item of the source
     * @param <R>    The type of a mapped item
     * @return The mapped items
     */
    public static <T, R> Publisher<R> map(Publisher<T> source, Function<? super T, ? extends R> mapper) {
        return new MapPublisher<>(source, mapper, null);
    }

    /**
     * Map each item of a source, and release the items the source drops: a Reactor source that is
     * cancelled discards the items it still holds through the discard hook of its subscriber.
     *
     * @param source  The source
     * @param mapper  Maps an item, which it takes over
     * @param discard Releases an item the source drops
     * @param <T>     The type of an item of the source
     * @param <R>     The type of a mapped item
     * @return The mapped items
     */
    public static <T, R> Publisher<R> map(Publisher<T> source, Function<? super T, ? extends R> mapper, Consumer<Object> discard) {
        return new MapPublisher<>(source, mapper, discard);
    }

    /**
     * One item, for one subscriber.
     *
     * @param item    The item
     * @param discard Releases the item when the subscriber cancels before it took it
     * @param <T>     The type of the item
     * @return The publisher of the item
     */
    public static <T> Publisher<T> just(T item, Consumer<? super T> discard) {
        return new JustPublisher<>(item, discard);
    }

    /**
     * The items of a source, then one more item once the source completed.
     *
     * @param source  The source
     * @param last    The item after the items of the source
     * @param discard Releases the last item when it is not delivered
     * @param <T>     The type of an item
     * @return The items
     */
    public static <T> Publisher<T> append(Publisher<T> source, T last, Consumer<? super T> discard) {
        return new AppendPublisher<>(source, last, discard);
    }

    /**
     * Wait for the first item of a source: the flow completes with a publisher of all the items
     * once the first item arrived, or once the source completed without items, and fails with a
     * failure before the first item. Cancelling the flow before anyone subscribed to the items
     * cancels the source and releases the first item.
     *
     * @param source  The source
     * @param discard Releases the first item when the subscriber of the items cancels before it
     *                took it, and the items that arrive after a cancellation
     * @param <T>     The type of an item
     * @return The flow of the items
     */
    public static <T> ExecutionFlow<Publisher<T>> awaitFirst(Publisher<T> source, Consumer<? super T> discard) {
        FirstItem<T> first = new FirstItem<>(discard);
        first.result.onCancel(first::abandon);
        ReactorInterop.subscribe(source, first, first::downstream, null);
        return first.result;
    }

    /**
     * A discard hook that closes the {@link ReadBuffer}s a source drops.
     *
     * @param item The item the source drops
     */
    public static void closeReadBuffer(Object item) {
        if (item instanceof ReadBuffer buffer) {
            buffer.close();
        }
    }

    /**
     * Reject a subscriber of a publisher that has one already: it gets a subscription that does
     * nothing, and an {@link IllegalStateException}.
     *
     * @param subscriber The subscriber
     * @param message    The message of the failure
     */
    public static void reject(Subscriber<?> subscriber, String message) {
        subscriber.onSubscribe(REJECTED);
        subscriber.onError(new IllegalStateException(message));
    }

    /**
     * The failure of a request for no items, which the reactive streams specification requires
     * (rule 3.9).
     *
     * @param n The number of items requested
     * @return The failure
     */
    public static IllegalArgumentException nonPositiveRequest(long n) {
        return new IllegalArgumentException("§3.9 violated: the number of items requested must be positive, but was " + n);
    }

    /**
     * Add to a demand, capped at {@link Long#MAX_VALUE}, which means an unbounded demand.
     *
     * @param demand The demand
     * @param n      The number of items requested, positive
     * @return The new demand
     */
    public static long addCap(long demand, long n) {
        return Long.MAX_VALUE - demand < n ? Long.MAX_VALUE : demand + n;
    }

    private record MapPublisher<T, R>(Publisher<T> source,
                                      Function<? super T, ? extends R> mapper,
                                      @Nullable Consumer<Object> discard) implements Publisher<R> {
        @Override
        public void subscribe(Subscriber<? super R> actual) {
            ReactorInterop.subscribe(source, new MapSubscriber<>(actual, mapper, discard), () -> actual, discard);
        }
    }

    /**
     * Passes the subscription of the source on: requests and cancellations go to the source.
     *
     * @param <T> The type of an item of the source
     * @param <R> The type of a mapped item
     */
    private static final class MapSubscriber<T, R> implements Subscriber<T> {
        private final Subscriber<? super R> actual;
        private final Function<? super T, ? extends R> mapper;
        private final @Nullable Consumer<Object> discard;
        private @Nullable Subscription upstream;
        private boolean done;

        MapSubscriber(Subscriber<? super R> actual, Function<? super T, ? extends R> mapper, @Nullable Consumer<Object> discard) {
            this.actual = actual;
            this.mapper = mapper;
            this.discard = discard;
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            actual.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            if (done) {
                if (discard != null) {
                    discard.accept(t);
                }
                return;
            }
            R mapped;
            try {
                mapped = Objects.requireNonNull(mapper.apply(t), "The mapper returned null");
            } catch (Throwable e) {
                done = true;
                Objects.requireNonNull(upstream).cancel();
                actual.onError(e);
                return;
            }
            actual.onNext(mapped);
        }

        @Override
        public void onError(Throwable t) {
            if (done) {
                return;
            }
            done = true;
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            actual.onComplete();
        }
    }

    private static final class JustPublisher<T> implements Publisher<T> {
        private final T item;
        private final Consumer<? super T> discard;
        private boolean subscribed;

        JustPublisher(T item, Consumer<? super T> discard) {
            this.item = item;
            this.discard = discard;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            synchronized (this) {
                if (subscribed) {
                    reject(subscriber, "The item is published to a single subscriber");
                    return;
                }
                subscribed = true;
            }
            subscriber.onSubscribe(new Subscription() {
                private boolean done;

                @Override
                public void request(long n) {
                    synchronized (this) {
                        if (done) {
                            return;
                        }
                        done = true;
                    }
                    if (n <= 0) {
                        discard.accept(item);
                        subscriber.onError(nonPositiveRequest(n));
                        return;
                    }
                    subscriber.onNext(item);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    synchronized (this) {
                        if (done) {
                            return;
                        }
                        done = true;
                    }
                    discard.accept(item);
                }
            });
        }
    }

    private record AppendPublisher<T>(Publisher<T> source, T last, Consumer<? super T> discard) implements Publisher<T> {
        @Override
        public void subscribe(Subscriber<? super T> actual) {
            ReactorInterop.subscribe(source, new AppendSubscriber<>(actual, last, discard), () -> actual, null);
        }
    }

    /**
     * Forwards the demand to the source while it emits, and delivers the last item when the
     * source completed and there is demand left.
     *
     * @param <T> The type of an item
     */
    private static final class AppendSubscriber<T> implements Subscriber<T>, Subscription {
        private final Subscriber<? super T> actual;
        private final T last;
        private final Consumer<? super T> discard;
        private @Nullable Subscription upstream;

        // guarded by this
        private long demand;
        private boolean sourceDone;
        /**
         * The last item was delivered or discarded, or the source failed.
         */
        private boolean lastDone;

        AppendSubscriber(Subscriber<? super T> actual, T last, Consumer<? super T> discard) {
            this.actual = actual;
            this.last = last;
            this.discard = discard;
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            actual.onSubscribe(this);
        }

        @Override
        public void onNext(T t) {
            synchronized (this) {
                if (demand != Long.MAX_VALUE) {
                    demand--;
                }
            }
            actual.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            boolean drop;
            synchronized (this) {
                drop = !lastDone;
                lastDone = true;
            }
            if (drop) {
                discard.accept(last);
            }
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                sourceDone = true;
            }
            deliverLast();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                // the source reports it
                Objects.requireNonNull(upstream).request(n);
                return;
            }
            boolean done;
            synchronized (this) {
                demand = addCap(demand, n);
                done = sourceDone;
            }
            if (done) {
                deliverLast();
            } else {
                Objects.requireNonNull(upstream).request(n);
            }
        }

        private void deliverLast() {
            synchronized (this) {
                if (!sourceDone || lastDone || demand <= 0) {
                    return;
                }
                lastDone = true;
            }
            actual.onNext(last);
            actual.onComplete();
        }

        @Override
        public void cancel() {
            boolean drop;
            synchronized (this) {
                drop = !lastDone;
                lastDone = true;
            }
            Objects.requireNonNull(upstream).cancel();
            if (drop) {
                discard.accept(last);
            }
        }
    }

    /**
     * Requests the first item of the source as soon as it subscribes, and holds it, or the end of
     * the source, until the subscriber of the items asks for it. Further items are only requested
     * by the subscriber of the items.
     *
     * @param <T> The type of an item
     */
    private static final class FirstItem<T> implements Subscriber<T>, Publisher<T>, Subscription {
        private final Consumer<? super T> discard;
        private final DelayedExecutionFlow<Publisher<T>> result = DelayedExecutionFlow.create();
        private @Nullable Subscription upstream;

        // guarded by this
        private @Nullable Subscriber<? super T> downstream;
        private boolean received;
        private @Nullable T first;
        /**
         * The first item was delivered or dropped: items pass through.
         */
        private boolean firstDone;
        private boolean sourceDone;
        private @Nullable Throwable failure;
        private boolean terminated;
        private boolean cancelled;
        /**
         * The flow was cancelled before anyone subscribed to the items.
         */
        private boolean abandoned;
        private long heldDemand;

        FirstItem(Consumer<? super T> discard) {
            this.discard = discard;
        }

        synchronized @Nullable Subscriber<? super T> downstream() {
            return downstream;
        }

        /**
         * The hook of the flow, which also runs when the flow is cancelled after it completed:
         * the items then belong to their subscriber.
         */
        void abandon() {
            synchronized (this) {
                if (downstream != null) {
                    return;
                }
                abandoned = true;
            }
            cancel();
        }

        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            s.request(1);
        }

        @Override
        public void onNext(T t) {
            boolean firstItem;
            boolean drop;
            Subscriber<? super T> d;
            synchronized (this) {
                drop = cancelled;
                firstItem = !received;
                received = true;
                if (firstItem && !drop) {
                    first = t;
                }
                d = downstream;
            }
            if (drop) {
                discard.accept(t);
            } else if (firstItem) {
                result.complete(this);
            } else {
                Objects.requireNonNull(d).onNext(t);
            }
        }

        @Override
        public void onError(Throwable t) {
            boolean beforeFirst;
            synchronized (this) {
                beforeFirst = !received;
                received = true;
                sourceDone = true;
                failure = t;
            }
            if (beforeFirst) {
                synchronized (this) {
                    terminated = true;
                }
                result.completeExceptionally(t);
            } else {
                deliverTerminal();
            }
        }

        @Override
        public void onComplete() {
            boolean beforeFirst;
            synchronized (this) {
                beforeFirst = !received;
                received = true;
                sourceDone = true;
                if (beforeFirst) {
                    // nothing to hold back
                    firstDone = true;
                }
            }
            if (beforeFirst) {
                result.complete(this);
            }
            deliverTerminal();
        }

        @Override
        public void subscribe(Subscriber<? super T> s) {
            boolean wasAbandoned;
            synchronized (this) {
                if (downstream != null) {
                    reject(s, "The items are published to a single subscriber");
                    return;
                }
                downstream = s;
                wasAbandoned = abandoned && !terminated;
                if (wasAbandoned) {
                    terminated = true;
                }
            }
            s.onSubscribe(this);
            if (wasAbandoned) {
                s.onError(new CancellationException("The items were cancelled before they were subscribed to"));
                return;
            }
            deliverTerminal();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                // the source reports it
                Objects.requireNonNull(upstream).request(n);
                return;
            }
            T item = null;
            Subscriber<? super T> d;
            long forward;
            synchronized (this) {
                d = downstream;
                if (cancelled || terminated) {
                    return;
                }
                if (!firstDone) {
                    if (first == null) {
                        // the first item is still on its way, or being delivered
                        heldDemand = addCap(heldDemand, n);
                        return;
                    }
                    item = first;
                    first = null;
                    n--;
                }
                forward = n;
            }
            if (item != null) {
                Objects.requireNonNull(d).onNext(item);
                synchronized (this) {
                    firstDone = true;
                    forward = addCap(forward, heldDemand);
                    heldDemand = 0;
                }
                deliverTerminal();
            }
            if (forward > 0 && !sourceDoneNow()) {
                Objects.requireNonNull(upstream).request(forward);
            }
        }

        private synchronized boolean sourceDoneNow() {
            return sourceDone;
        }

        /**
         * Deliver the end of the source once the subscriber of the items has the first item.
         */
        private void deliverTerminal() {
            Subscriber<? super T> d;
            Throwable error;
            synchronized (this) {
                d = downstream;
                if (d == null || !sourceDone || !firstDone || terminated || cancelled) {
                    return;
                }
                terminated = true;
                error = failure;
            }
            if (error != null) {
                d.onError(error);
            } else {
                d.onComplete();
            }
        }

        @Override
        public void cancel() {
            T item;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                item = first;
                first = null;
            }
            if (item != null) {
                discard.accept(item);
            }
            Subscription s = upstream;
            if (s != null) {
                s.cancel();
            }
        }
    }

    /**
     * A publisher of items that are pushed to it, for one subscriber, like a unicast sink that
     * buffers: the items that arrive before they are requested are queued, and a failure is
     * delivered after the queued items. A cancellation releases the queued items. Signals are
     * delivered one at a time, never while the subscriber runs {@code onSubscribe}, and a request
     * from {@code onNext} is not delivered reentrantly.
     * <p>
     * The hooks run around the signals: {@link #onSubscribing()} before the subscriber gets its
     * subscription, {@link #beforeNext} before an item is delivered, {@link #beforeError()} before
     * the failure is delivered, {@link #discard} for an item that is not delivered, and
     * {@link #onCancelled()} once, when the subscriber cancels or makes a request for no items.
     *
     * @param <T> The type of an item
     */
    public static class Unicast<T> implements Publisher<T>, Subscription {
        // all of the following are guarded by this
        private final ArrayDeque<T> queue = new ArrayDeque<>(2);
        /**
         * A subscriber subscribed; a later one is rejected.
         */
        private boolean claimed;
        /**
         * The subscriber, until it cancels or receives its terminal signal (rule 3.13).
         */
        private @Nullable Subscriber<? super T> subscriber;
        /**
         * The subscriber has its subscription, so signals may be delivered.
         */
        private boolean subscribed;
        private long demand;
        /**
         * The items ended: completed or failed.
         */
        private boolean done;
        private @Nullable Throwable failure;
        private boolean cancelled;
        /**
         * The failure of a request for no items, delivered instead of anything else.
         */
        private @Nullable Throwable badRequest;
        /**
         * The subscriber received its terminal signal.
         */
        private boolean terminated;
        /**
         * A thread delivers signals; another one that has signals to deliver leaves them to it.
         */
        private boolean draining;
        private boolean missed;

        /**
         * Runs when the subscriber subscribes, before it gets its subscription.
         */
        protected void onSubscribing() {
        }

        /**
         * Runs before an item is delivered.
         *
         * @param item The item
         */
        protected void beforeNext(T item) {
        }

        /**
         * Runs before the failure of the items is delivered.
         */
        protected void beforeError() {
        }

        /**
         * Releases an item that is not delivered: queued when the subscriber cancelled, or pushed
         * after the end.
         *
         * @param item The item
         */
        protected void discard(T item) {
        }

        /**
         * Runs once, when the subscriber cancels or makes a request for no items.
         */
        protected void onCancelled() {
        }

        /**
         * Push an item.
         *
         * @param item The item, which this takes over when it is accepted
         * @return Whether the item was accepted: not after a cancellation or the end. An item that
         * is not accepted is not discarded
         */
        public final boolean tryNext(T item) {
            synchronized (this) {
                if (done || cancelled || terminated) {
                    return false;
                }
                queue.add(item);
            }
            drain();
            return true;
        }

        /**
         * End the items.
         *
         * @return Whether this ended them: not when they already ended
         */
        public final boolean tryComplete() {
            synchronized (this) {
                if (done) {
                    return false;
                }
                done = true;
            }
            drain();
            return true;
        }

        /**
         * Fail the items, after the queued ones.
         *
         * @param e The failure
         * @return Whether this failed them: not when they already ended
         */
        public final boolean tryError(Throwable e) {
            synchronized (this) {
                if (done) {
                    return false;
                }
                done = true;
                failure = e;
            }
            drain();
            return true;
        }

        @Override
        public final void subscribe(Subscriber<? super T> s) {
            boolean accepted;
            synchronized (this) {
                accepted = !claimed;
                if (accepted) {
                    claimed = true;
                    subscriber = s;
                }
            }
            if (!accepted) {
                reject(s, "The items are published to a single subscriber");
                return;
            }
            onSubscribing();
            s.onSubscribe(this);
            synchronized (this) {
                subscribed = true;
            }
            // the end needs no demand
            drain();
        }

        @Override
        public final void request(long n) {
            synchronized (this) {
                if (n <= 0) {
                    if (badRequest == null) {
                        badRequest = nonPositiveRequest(n);
                    }
                } else {
                    demand = addCap(demand, n);
                }
            }
            drain();
        }

        @Override
        public final void cancel() {
            Object[] dropped;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                subscriber = null;
                if (badRequest != null && terminated) {
                    // already released
                    return;
                }
                dropped = queue.toArray();
                queue.clear();
            }
            discardAll(dropped);
            onCancelled();
        }

        @SuppressWarnings("unchecked")
        private void discardAll(Object[] dropped) {
            for (Object item : dropped) {
                discard((T) item);
            }
        }

        private void drain() {
            synchronized (this) {
                if (draining) {
                    missed = true;
                    return;
                }
                draining = true;
            }
            while (true) {
                Subscriber<? super T> s;
                T next = null;
                Throwable error = null;
                Object[] dropped = null;
                synchronized (this) {
                    s = subscriber;
                    if (s == null || !subscribed || cancelled || terminated) {
                        draining = false;
                        missed = false;
                        return;
                    }
                    if (badRequest != null) {
                        terminated = true;
                        subscriber = null;
                        error = badRequest;
                        dropped = queue.toArray();
                        queue.clear();
                    } else if (demand > 0 && !queue.isEmpty()) {
                        next = queue.poll();
                        if (demand != Long.MAX_VALUE) {
                            demand--;
                        }
                    } else if (done && queue.isEmpty()) {
                        terminated = true;
                        subscriber = null;
                        error = failure;
                    } else if (missed) {
                        missed = false;
                        continue;
                    } else {
                        draining = false;
                        return;
                    }
                }
                if (dropped != null) {
                    discardAll(dropped);
                    onCancelled();
                    s.onError(Objects.requireNonNull(error));
                } else if (next != null) {
                    beforeNext(next);
                    s.onNext(next);
                } else if (error == null) {
                    s.onComplete();
                } else {
                    beforeError();
                    s.onError(error);
                }
            }
        }
    }
}
