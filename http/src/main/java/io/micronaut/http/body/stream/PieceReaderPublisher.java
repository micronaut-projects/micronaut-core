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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.body.PieceReader;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The elements a {@link PieceReader} reads from a publisher of pieces, as a publisher, without
 * Reactor: an element is polled for each element requested, and a piece is requested only when
 * the pieces read so far complete no other element. The calls of the reader are serialized by
 * the drain loop.
 *
 * <p>One subscriber.</p>
 *
 * @param <I> The type of a piece of the input
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class PieceReaderPublisher<I, T> implements Publisher<T>, Subscription, CoreSubscriber<I> {

    private final Publisher<I> input;
    private final Function<? super I, ReadBuffer> adapter;
    private final Consumer<? super I> discard;
    private final Consumer<Object> foreignDiscard;
    private final PieceReader<T> reader;

    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicInteger wip = new AtomicInteger();
    private final AtomicLong requested = new AtomicLong();
    /**
     * The piece that arrived and is not read yet: one piece is requested at a time.
     */
    private final AtomicReference<@Nullable I> piece = new AtomicReference<>();

    private final AtomicReference<@Nullable Subscriber<? super T>> downstream = new AtomicReference<>();
    private final AtomicReference<@Nullable Subscription> upstream = new AtomicReference<>();
    private volatile boolean pieceRequested;
    private volatile boolean inputEnded;
    private final AtomicReference<@Nullable Throwable> inputFailure = new AtomicReference<>();
    /**
     * The §3.9 failure of a request that is not positive, signalled instead of the cancellation.
     */
    private final AtomicReference<@Nullable Throwable> badRequest = new AtomicReference<>();
    /**
     * The input emitted more pieces than were requested: it is cancelled.
     */
    private volatile boolean overflow;
    /**
     * The context of a Reactor input, with the discard hook: created once.
     */
    private Context context;
    private volatile boolean cancelled;

    // only accessed by the drain loop
    private boolean readerCompleted;
    /**
     * The reader failed to read or complete the input: the elements it holds are emitted
     * before this failure. Only touched by the drain.
     */
    private @Nullable Throwable readerFailure;
    private boolean done;
    private boolean upstreamCancelRequested;
    private boolean upstreamCancelled;
    private @Nullable T held;

    /**
     * @param input   The pieces of the body
     * @param adapter The buffer of a piece, which takes it over
     * @param discard Releases a piece that is not read
     * @param reader  The reader of the pieces, which the publisher takes over
     */
    PieceReaderPublisher(Publisher<I> input, Function<? super I, ReadBuffer> adapter, Consumer<? super I> discard, PieceReader<T> reader) {
        this(input, adapter, discard, reader, object -> { });
    }

    /**
     * @param input          The pieces of the body
     * @param adapter        The buffer of a piece, which takes it over
     * @param discard        Releases a piece that is not read
     * @param reader         The reader of the pieces, which the publisher takes over
     * @param foreignDiscard Releases another object that a Reactor input discards, e.g. a Netty
     *                       buffer it held before mapping it to a piece: this module does not
     *                       know Netty, so the Netty modules pass the release of its reference
     *                       counted objects
     */
    PieceReaderPublisher(Publisher<I> input, Function<? super I, ReadBuffer> adapter, Consumer<? super I> discard, PieceReader<T> reader, Consumer<Object> foreignDiscard) {
        this.input = input;
        this.adapter = adapter;
        this.discard = discard;
        this.reader = reader;
        this.foreignDiscard = foreignDiscard;
        this.context = Operators.enableOnDiscard(Context.empty(), this::discardObject);
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
            subscriber.onError(new IllegalStateException("The elements of a body can be subscribed to only once"));
            return;
        }
        downstream.set(subscriber);
        context = Operators.enableOnDiscard(subscriber instanceof CoreSubscriber<?> coreSubscriber
            ? coreSubscriber.currentContext() : Context.empty(), this::discardObject);
        subscriber.onSubscribe(this);
        input.subscribe(this);
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            badRequest.compareAndSet(null, new IllegalArgumentException("§3.9: the number of requested elements must be positive: " + n));
            cancelled = true;
        } else {
            long current;
            long next;
            do {
                current = requested.get();
                if (current == Long.MAX_VALUE) {
                    break;
                }
                next = current + n;
                if (next < 0) {
                    next = Long.MAX_VALUE;
                }
            } while (!requested.compareAndSet(current, next));
        }
        drain();
    }

    @Override
    public void cancel() {
        cancelled = true;
        drain();
    }

    /**
     * The pieces a Reactor input still holds when it is cancelled are discarded with the
     * discard hook of the context of its subscriber: interop only, nothing of Reactor is on the
     * path of the elements. A {@link ReadBuffer} is closed and a Micronaut
     * {@link ReferenceCounted} is released here; any other object, such as a Netty buffer that
     * an operator held before it was mapped to a piece, is passed to the foreign discard of the
     * publisher, which the Netty modules set to release Netty reference counted objects.
     *
     * @return The context with the discard hook
     */
    @Override
    public Context currentContext() {
        return context;
    }

    private void discardObject(Object piece) {
        if (piece instanceof ReadBuffer readBuffer) {
            readBuffer.close();
        } else if (piece instanceof ReferenceCounted counted) {
            counted.release();
        } else {
            foreignDiscard.accept(piece);
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        upstream.set(s);
        drain();
    }

    @Override
    public void onNext(I next) {
        if (!piece.compareAndSet(null, next)) {
            // §1.1: more pieces than requested; the input is cancelled
            discard.accept(next);
            overflow = true;
            inputFailure.compareAndSet(null, new IllegalStateException("The input emitted more pieces than were requested"));
        }
        drain();
    }

    @Override
    public void onError(Throwable t) {
        inputFailure.set(t);
        drain();
    }

    @Override
    public void onComplete() {
        inputEnded = true;
        drain();
    }

    private void drain() {
        if (wip.getAndIncrement() != 0) {
            return;
        }
        int missed = 1;
        while (true) {
            if (done) {
                cancelUpstream();
                discardPieces();
            } else {
                drainOnce();
            }
            missed = wip.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    /**
     * Emit the elements that are requested and available, read the pieces that arrived, and
     * request the next piece when the pieces read so far complete no requested element.
     */
    @SuppressWarnings({"java:S135", "java:S1181"}) // Drain transitions preserve piece ownership; reader failures must reach onError.
    private void drainOnce() {
        Subscriber<? super T> subscriber = downstream.get();
        if (subscriber == null) {
            return;
        }
        long demand = requested.get();
        long emitted = 0;
        try {
            while (true) {
                if (cancelled) {
                    Throwable failure = badRequest.get();
                    terminate(true);
                    if (failure != null) {
                        // §3.9
                        subscriber.onError(failure);
                    }
                    return;
                }
                Throwable failure = inputFailure.get();
                if (failure != null && readerFailure == null) {
                    // the input failed: the elements not emitted yet are dropped
                    terminate(overflow);
                    subscriber.onError(failure);
                    return;
                }
                if (emitted < demand) {
                    T element = held;
                    held = null;
                    if (element == null) {
                        element = reader.poll();
                    }
                    if (element != null) {
                        subscriber.onNext(element);
                        emitted++;
                        continue;
                    }
                } else if (!(inputEnded || readerFailure != null) || held != null) {
                    // no demand: nothing more is read from the input
                    break;
                }
                // Observe completion before taking the last piece published before it.
                boolean ended = inputEnded;
                I next = piece.getAndSet(null);
                if (next != null) {
                    // the request is answered when the drain takes the piece, not in onNext: only the drain touches the flag
                    pieceRequested = false;
                    if (readerFailure == null) {
                        readPiece(next);
                    } else {
                        discard.accept(next);
                    }
                    continue;
                }
                if (ended || readerFailure != null) {
                    if (!readerCompleted) {
                        readerCompleted = true;
                        completeReader();
                        continue;
                    }
                    // the end, also without demand: an element the end completed waits for one
                    T last = emitted < demand ? null : reader.poll();
                    if (last != null) {
                        held = last;
                        break;
                    }
                    terminate(false);
                    if (readerFailure != null) {
                        subscriber.onError(readerFailure);
                    } else {
                        subscriber.onComplete();
                    }
                    return;
                }
                Subscription s = upstream.get();
                if (s != null && !pieceRequested) {
                    pieceRequested = true;
                    s.request(1);
                    // the piece may have arrived during the call
                    continue;
                }
                break;
            }
        } catch (Exception | Error e) {
            terminate(true);
            subscriber.onError(e);
            return;
        }
        if (emitted != 0 && demand != Long.MAX_VALUE) {
            requested.addAndGet(-emitted);
        }
    }

    @SuppressWarnings("java:S1181") // Preserve decoded elements before signalling any reader failure.
    private void readPiece(I next) {
        try {
            reader.read(adapter.apply(next));
        } catch (Exception | Error e) {
            // Emit the values the piece completed before its failure, like the reactive readers.
            readerFailure = e;
            readerCompleted = true;
            upstreamCancelRequested = true;
            cancelUpstream();
        }
    }

    @SuppressWarnings("java:S1181") // Report final reader failures through the subscriber.
    private void completeReader() {
        try {
            reader.complete();
        } catch (Exception | Error e) {
            // Emit preceding values before reporting an incomplete final value.
            readerFailure = e;
        }
    }

    /**
     * @param cancelUpstream Whether the input is cancelled: it did not end
     */
    private void terminate(boolean cancelUpstream) {
        done = true;
        T last = held;
        held = null;
        if (last != null) {
            // an element the end of the input completed, not emitted: e.g. a reference counted
            // buffer
            discardObject(last);
        }
        reader.close();
        if (cancelUpstream) {
            upstreamCancelRequested = true;
            cancelUpstream();
        }
        discardPieces();
    }

    private void cancelUpstream() {
        Subscription s = upstream.get();
        if (s != null && upstreamCancelRequested && !upstreamCancelled) {
            upstreamCancelled = true;
            s.cancel();
        }
    }

    private void discardPieces() {
        I next = piece.getAndSet(null);
        if (next != null) {
            discard.accept(next);
        }
    }
}
