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
package io.micronaut.http.server;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.Objects;
import java.util.function.Function;

/**
 * The pieces of a response whose body is a publisher, without Reactor operators. Each item of the
 * body is written to a piece, in order and one at a time: an item is requested once the piece of
 * the previous one was taken, and the subscriber of the pieces controls the demand.
 * <p>
 * The flow of {@link #write} completes once the first piece is written, or once the body ends
 * without items, with the publisher of the pieces, so that a failure before the first piece can
 * still be answered with an error response: the flow fails with it. A failure after the first
 * piece fails the pieces once the pieces before it were taken.
 * <p>
 * A piece that cannot be taken, because the subscriber cancelled or the pieces failed, is closed.
 * A Reactor body sees a discard hook that closes the {@link CloseableByteBody} items it drops.
 * When the pieces complete, fail or are cancelled, they end: the given callback runs once.
 *
 * @param <T> The type of an item of the body
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ResponsePieces<T> implements CoreSubscriber<T>, Publisher<ByteBody>, Subscription {
    private static final Context DISCARD_BODIES = Operators.enableOnDiscard(Context.empty(), ResponsePieces::discard);
    private static final Subscription REJECTED = new Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
        }
    };

    private final Function<? super T, ? extends ExecutionFlow<? extends CloseableByteBody>> writer;
    private final Runnable onEnd;
    private final DelayedExecutionFlow<Publisher<ByteBody>> result = DelayedExecutionFlow.create();

    // all of the following are guarded by this
    private @Nullable Subscription upstream;
    private @Nullable Subscriber<? super ByteBody> downstream;
    /**
     * The subscriber of the pieces has its subscription, so signals may be delivered.
     */
    private boolean subscribed;
    private long demand;
    /**
     * An item was requested from the body and has not arrived yet.
     */
    private boolean requested;
    /**
     * An item is being written.
     */
    private boolean writing;
    /**
     * The flow of the item being written, while it is pending. Cancelled with the pieces.
     */
    private @Nullable ExecutionFlow<? extends CloseableByteBody> pendingFlow;
    /**
     * The number of items whose writing has started, and the number of those that are done.
     */
    private long started;
    private long finished;
    /**
     * A written piece that waits to be taken. At most one item is requested, written or waiting.
     */
    private @Nullable CloseableByteBody ready;
    /**
     * The body completed or failed.
     */
    private boolean bodyDone;
    /**
     * The failure of the body or of a write, delivered once the piece before it was taken.
     */
    private @Nullable Throwable failure;
    /**
     * The flow of {@link #write} completed or failed.
     */
    private boolean resultDone;
    private boolean cancelled;
    /**
     * The subscriber of the pieces received its terminal signal, or the flow failed.
     */
    private boolean terminated;
    private boolean ended;
    /**
     * A thread delivers signals; another one that has signals to deliver leaves them to it.
     */
    private boolean draining;
    private boolean missed;

    private ResponsePieces(Function<? super T, ? extends ExecutionFlow<? extends CloseableByteBody>> writer, Runnable onEnd) {
        this.writer = writer;
        this.onEnd = onEnd;
    }

    /**
     * Write the items of a body to pieces.
     *
     * @param body   The body
     * @param writer Writes an item to a piece; a flow without a value writes no piece
     * @param onEnd  Runs once when the pieces complete, fail or are cancelled
     * @param <T>    The type of an item of the body
     * @return A flow that completes with the publisher of the pieces once the first piece is
     * written or the body ended without items, or that fails with a failure before the first piece
     */
    static <T> ExecutionFlow<Publisher<ByteBody>> write(Publisher<T> body,
                                                        Function<? super T, ? extends ExecutionFlow<? extends CloseableByteBody>> writer,
                                                        Runnable onEnd) {
        ResponsePieces<T> pieces = new ResponsePieces<>(writer, onEnd);
        pieces.result.onCancel(pieces::abandon);
        body.subscribe(pieces);
        return pieces.result;
    }

    /**
     * A response that is abandoned before anyone took its pieces cancels the body. The hook of a
     * flow also runs when the flow is cancelled after it completed: the pieces then belong to
     * their subscriber.
     */
    private void abandon() {
        synchronized (this) {
            if (downstream != null) {
                return;
            }
        }
        cancel();
    }

    private static void discard(Object item) {
        if (item instanceof CloseableByteBody body) {
            body.close();
        }
    }

    @Override
    public Context currentContext() {
        return DISCARD_BODIES;
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean cancel;
        synchronized (this) {
            cancel = upstream != null || cancelled;
            if (upstream == null) {
                upstream = s;
            }
        }
        if (cancel) {
            s.cancel();
        } else {
            // requests the first item
            drain();
        }
    }

    @Override
    public void onNext(T item) {
        long number;
        boolean drop;
        synchronized (this) {
            requested = false;
            drop = cancelled || terminated;
            if (!drop) {
                writing = true;
                number = ++started;
            } else {
                number = 0;
            }
        }
        if (drop) {
            discard(item);
            return;
        }
        ExecutionFlow<? extends CloseableByteBody> flow;
        try {
            flow = Objects.requireNonNull(writer.apply(item), "The writer returned a null flow");
        } catch (Throwable e) {
            discard(item);
            written(null, e);
            return;
        }
        ImperativeExecutionFlow<? extends CloseableByteBody> complete = flow.tryComplete();
        if (complete != null) {
            written(complete.getValue(), complete.getError());
            return;
        }
        // registered before the flow can be cancelled, which rejects further steps. A piece that
        // is written after a cancellation is closed in written
        flow.onComplete(this::written);
        boolean cancelNow;
        synchronized (this) {
            // the pieces may have been cancelled while the writer ran. The flow is only kept while
            // it has not completed: written may already have run
            cancelNow = cancelled;
            if (!cancelNow && finished < number) {
                pendingFlow = flow;
            }
        }
        if (cancelNow) {
            flow.cancel();
        }
    }

    private void written(@Nullable CloseableByteBody piece, @Nullable Throwable error) {
        boolean drop;
        boolean drain = false;
        synchronized (this) {
            pendingFlow = null;
            finished++;
            writing = false;
            drop = cancelled || terminated;
            if (!drop) {
                if (error != null) {
                    if (failure == null) {
                        failure = error;
                    }
                } else {
                    ready = piece;
                }
                drain = enterDrain();
            }
        }
        if (drop) {
            if (piece != null) {
                piece.close();
            }
        } else if (drain) {
            drainLoop();
        }
    }

    @Override
    public void onError(Throwable t) {
        synchronized (this) {
            if (bodyDone || terminated || cancelled) {
                return;
            }
            bodyDone = true;
            // no item answers the request any more
            requested = false;
            if (failure == null) {
                failure = t;
            }
        }
        drain();
    }

    @Override
    public void onComplete() {
        synchronized (this) {
            if (bodyDone || terminated || cancelled) {
                return;
            }
            bodyDone = true;
            requested = false;
        }
        drain();
    }

    @Override
    public void subscribe(Subscriber<? super ByteBody> s) {
        boolean accepted;
        synchronized (this) {
            accepted = downstream == null;
            if (accepted) {
                downstream = s;
            }
        }
        if (!accepted) {
            s.onSubscribe(REJECTED);
            s.onError(new IllegalStateException("The pieces of a response are published to a single subscriber"));
            return;
        }
        s.onSubscribe(this);
        synchronized (this) {
            subscribed = true;
        }
        // the end of a body without items needs no demand
        drain();
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            return;
        }
        boolean drain;
        synchronized (this) {
            demand = Long.MAX_VALUE - demand < n ? Long.MAX_VALUE : demand + n;
            drain = enterDrain();
        }
        if (drain) {
            drainLoop();
        }
    }

    @Override
    public void cancel() {
        CloseableByteBody piece;
        ExecutionFlow<? extends CloseableByteBody> flow;
        Subscription s;
        synchronized (this) {
            if (cancelled || terminated) {
                return;
            }
            cancelled = true;
            piece = ready;
            ready = null;
            flow = pendingFlow;
            pendingFlow = null;
            s = upstream;
        }
        if (s != null) {
            s.cancel();
        }
        if (flow != null) {
            flow.cancel();
        }
        if (piece != null) {
            piece.close();
        }
        end();
    }

    private void drain() {
        boolean drain;
        synchronized (this) {
            drain = enterDrain();
        }
        if (drain) {
            drainLoop();
        }
    }

    /**
     * Must hold the lock.
     *
     * @return Whether the caller delivers the signals, otherwise the thread that does is told
     * to look again
     */
    private boolean enterDrain() {
        if (draining) {
            missed = true;
            return false;
        }
        draining = true;
        return true;
    }

    /**
     * Deliver what is due: complete or fail the flow of {@link #write} before the first piece,
     * then the pieces the subscriber requested, then the completion or the failure once no piece
     * is pending, and request the next item when a piece was taken.
     * <p>
     * The next item is requested after this loop let go: a body that emits it while it is
     * requested writes it and delivers it itself, inside its own loop, instead of being asked
     * again for every item. A body bounds that recursion, as the reactive streams specification
     * requires.
     */
    private void drainLoop() {
        while (true) {
            Action action;
            CloseableByteBody piece = null;
            Throwable error = null;
            Subscriber<? super ByteBody> s;
            Subscription up;
            synchronized (this) {
                s = downstream;
                up = upstream;
                action = next();
                if (action == Action.EMIT) {
                    piece = ready;
                    ready = null;
                    if (demand != Long.MAX_VALUE) {
                        demand--;
                    }
                } else if (action == Action.FAIL || action == Action.FAIL_RESULT) {
                    error = failure;
                } else if (action == Action.REQUEST) {
                    // the only action: nothing is in flight, and what arrives later drains again
                    draining = false;
                    missed = false;
                } else if (action == Action.IDLE) {
                    if (missed) {
                        missed = false;
                        continue;
                    }
                    draining = false;
                    return;
                }
            }
            switch (action) {
                case REQUEST -> {
                    Objects.requireNonNull(up).request(1);
                    return;
                }
                case COMPLETE_RESULT -> result.complete(this);
                case FAIL_RESULT -> {
                    cancelBody(up);
                    end();
                    result.completeExceptionally(Objects.requireNonNull(error));
                }
                case EMIT -> Objects.requireNonNull(s).onNext(Objects.requireNonNull(piece));
                case FAIL -> {
                    cancelBody(up);
                    end();
                    Objects.requireNonNull(s).onError(Objects.requireNonNull(error));
                }
                case COMPLETE -> {
                    end();
                    Objects.requireNonNull(s).onComplete();
                }
                default -> throw new IllegalStateException("Unexpected action " + action);
            }
        }
    }

    /**
     * Must hold the lock.
     *
     * @return The next action, with the state updated for it
     */
    private Action next() {
        if (cancelled || terminated) {
            return Action.IDLE;
        }
        boolean idleBody = !requested && !writing && ready == null;
        if (!resultDone) {
            if (ready != null || (idleBody && bodyDone && failure == null)) {
                resultDone = true;
                return Action.COMPLETE_RESULT;
            }
            if (idleBody && failure != null) {
                resultDone = true;
                terminated = true;
                return Action.FAIL_RESULT;
            }
            if (idleBody && !bodyDone && upstream != null) {
                // the first item, which the flow waits for
                requested = true;
                return Action.REQUEST;
            }
            return Action.IDLE;
        }
        if (!subscribed) {
            return Action.IDLE;
        }
        if (ready != null) {
            return demand > 0 ? Action.EMIT : Action.IDLE;
        }
        if (requested || writing) {
            return Action.IDLE;
        }
        if (failure != null) {
            terminated = true;
            return Action.FAIL;
        }
        if (bodyDone) {
            terminated = true;
            return Action.COMPLETE;
        }
        if (demand > 0 && upstream != null) {
            requested = true;
            return Action.REQUEST;
        }
        return Action.IDLE;
    }

    /**
     * Cancel the body after a failure to write, unless the body failed or completed itself.
     */
    private void cancelBody(@Nullable Subscription up) {
        boolean cancel;
        synchronized (this) {
            cancel = !bodyDone;
        }
        if (cancel && up != null) {
            up.cancel();
        }
    }

    private void end() {
        synchronized (this) {
            if (ended) {
                return;
            }
            ended = true;
        }
        onEnd.run();
    }

    private enum Action {
        IDLE,
        REQUEST,
        COMPLETE_RESULT,
        FAIL_RESULT,
        EMIT,
        FAIL,
        COMPLETE
    }
}
