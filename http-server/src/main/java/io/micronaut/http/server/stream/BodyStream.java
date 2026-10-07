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
package io.micronaut.http.server.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.http.exceptions.StreamOverflowException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A response body pushed from any thread, with backpressure from the connection and without
 * Reactive Streams: the core of {@link DefaultSseEmitter} and of {@link ElementsBody}.
 *
 * <p>The body is a streaming body of the {@link ByteBodyFactory} of the response, whose buffer is
 * fed on the {@link ByteBodyFactory#streamingBodyExecutor() executor} of the factory (the event
 * loop of the connection on Netty), in the order of the writes.</p>
 *
 * <p>Backpressure: the stream counts the bytes written but not yet taken by the connection, which
 * reports what it took with {@link #onBytesConsumed(long)} (on Netty: the bytes it wrote while the
 * channel was writable). The stage of a write completes once the bytes of the write and of the
 * writes before it that the connection has not taken are below the high-water mark. A write
 * while sixteen times the high-water mark are queued fails the stream.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BodyStream implements BufferConsumer.Upstream {

    /**
     * The bound of the queue, as a multiple of the high-water mark.
     */
    static final int BUFFER_LIMIT_FACTOR = 16;

    private static final Logger LOG = LoggerFactory.getLogger(BodyStream.class);
    private static final CompletionStage<Void> DONE = CompletableFuture.completedStage(null);

    private final ReentrantLock lock = new ReentrantLock();
    private final ByteBodyFactory factory;
    private final BufferConsumer buffer;
    private final CloseableByteBody body;
    private final Executor executor;
    private final PropagatedContext context;
    /**
     * Whether the body is never written: the response of a HEAD request. A discard is then a
     * normal end.
     */
    private final boolean bodyless;
    /**
     * The calls to the buffer, run in order on the executor.
     */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingTasks = new AtomicInteger();

    // guarded by lock
    private int highWaterMark;
    /**
     * The bytes written so far.
     */
    private long written;
    /**
     * The bytes the connection took so far. May exceed {@link #written} if the connection asks
     * for more in advance.
     */
    private long consumed;
    private final ArrayDeque<Waiter> waiters = new ArrayDeque<>();
    private State state = State.OPEN;
    /**
     * Why the stream closed, what a write after the close fails with.
     */
    private @Nullable Throwable cause;
    private @Nullable List<Consumer<@Nullable Throwable>> closeCallbacks;
    /**
     * Called when the stream became writable, for a pulling producer.
     */
    private @Nullable Runnable demandListener;

    /**
     * @param factory       The body factory of the response
     * @param context       The context of the producer, in which the stages complete
     * @param highWaterMark The high-water mark
     * @param bodyless      Whether the body is never written, the response of a HEAD request
     */
    BodyStream(ByteBodyFactory factory, PropagatedContext context, int highWaterMark, boolean bodyless) {
        this.factory = factory;
        this.executor = factory.streamingBodyExecutor();
        this.context = context;
        this.highWaterMark = checkHighWaterMark(highWaterMark);
        this.bodyless = bodyless;
        ByteBodyFactory.StreamingBody streamingBody = factory.createStreamingBody(BodySizeLimits.UNLIMITED, this);
        this.buffer = streamingBody.sharedBuffer();
        this.body = streamingBody.rootBody();
    }

    /**
     * @return The body, to be sent or closed once
     */
    CloseableByteBody body() {
        return body;
    }

    /**
     * @return Whether blocking the current thread would block an event loop
     */
    boolean isEventLoopThread() {
        return factory.isEventLoopThread();
    }

    /**
     * @param bytes The new high-water mark
     */
    void highWaterMark(int bytes) {
        checkHighWaterMark(bytes);
        Effects effects;
        lock.lock();
        try {
            highWaterMark = bytes;
            effects = releaseWaiters(null);
        } finally {
            lock.unlock();
        }
        if (effects != null) {
            effects.run();
        }
    }

    private static int checkHighWaterMark(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("The high-water mark must be positive: " + bytes);
        }
        return bytes;
    }

    /**
     * Write bytes. Ownership of the buffer passes to this method.
     *
     * @param data The bytes
     * @return Completes when the bytes are below the high-water mark, fails if the stream is
     * closed or overflows
     */
    CompletionStage<Void> write(ReadBuffer data) {
        // a write that neither closes the stream nor completes a stage only needs a drain: no
        // effects are allocated for it
        Effects effects = null;
        CompletionStage<Void> result;
        lock.lock();
        try {
            Throwable closed = cause;
            if (state != State.OPEN) {
                data.close();
                return CompletableFuture.failedStage(closed == null ? new IllegalStateException("The stream is closed") : closed);
            }
            long n = data.readable();
            long limit = (long) highWaterMark * BUFFER_LIMIT_FACTOR;
            if (written - consumed >= limit) {
                // bounded before the write: a single write larger than the limit still goes
                data.close();
                Throwable overflow = new StreamOverflowException("The client does not read the stream fast enough: "
                    + (written - consumed) + " bytes are queued, the limit is " + limit + " bytes (sixteen times the high-water mark). The stream was closed");
                effects = new Effects();
                closeLocked(State.FAILED, overflow, overflow, effects);
                submitLocked(() -> buffer.error(overflow), effects);
                result = CompletableFuture.failedStage(overflow);
            } else {
                written += n;
                // queued under the lock: the tasks run in the order of the writes
                tasks.add(() -> buffer.add(data));
                if (written - consumed < highWaterMark) {
                    result = DONE;
                } else {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    waiters.add(new Waiter(written, future));
                    result = future;
                }
            }
        } finally {
            lock.unlock();
        }
        if (effects != null) {
            effects.run();
        } else {
            scheduleDrain();
        }
        return result;
    }

    /**
     * End the body. The pending writes complete.
     *
     * @return Whether this closed the stream
     */
    boolean complete() {
        return complete("The stream is complete");
    }

    /**
     * End the body. The pending writes complete.
     *
     * @param reason Why the stream ended, the message of the failure of a later write
     * @return Whether this closed the stream
     */
    boolean complete(String reason) {
        Effects effects = new Effects();
        lock.lock();
        try {
            if (state != State.OPEN) {
                return false;
            }
            closeLocked(State.COMPLETED, new IllegalStateException(reason), null, effects);
            submitLocked(buffer::complete, effects);
        } finally {
            lock.unlock();
        }
        effects.run();
        return true;
    }

    /**
     * Fail the body. The pending writes fail with the cause.
     *
     * @param failure The failure
     * @return Whether this closed the stream
     */
    boolean fail(Throwable failure) {
        Effects effects = new Effects();
        lock.lock();
        try {
            if (state != State.OPEN) {
                return false;
            }
            closeLocked(State.FAILED, failure, failure, effects);
            submitLocked(() -> buffer.error(failure), effects);
        } finally {
            lock.unlock();
        }
        effects.run();
        return true;
    }

    /**
     * Close the stream because the body will not be sent: the response failed before it was sent.
     * The body is closed.
     *
     * @param failure The failure, for the pending writes and the callbacks
     * @return Whether this closed the stream, which was open
     */
    boolean abandon(Throwable failure) {
        return abandon(failure, () -> true);
    }

    /**
     * Close the stream because the body will not be sent, if it is open and the response is
     * refused: the decision is atomic with the state of the stream, so a stream that completed
     * meanwhile is not refused. The body is closed.
     *
     * @param failure The failure, for the pending writes and the callbacks
     * @param refuse  Refuses the response, under the lock of the stream: {@code false} if the
     *                response was sent meanwhile
     * @return Whether this closed the stream
     */
    boolean abandon(Throwable failure, BooleanSupplier refuse) {
        Effects effects = new Effects();
        lock.lock();
        try {
            if (state != State.OPEN || !refuse.getAsBoolean()) {
                return false;
            }
            closeLocked(State.FAILED, failure, failure, effects);
        } finally {
            lock.unlock();
        }
        effects.run();
        body.close();
        return true;
    }

    /**
     * End the stream without sending its body, if it is open and the response is replaced by
     * another one: the decision is atomic with the state of the stream. The body is closed, and
     * the callbacks run with {@code null}: the stream ended normally.
     *
     * @param replace Replaces the response, under the lock of the stream: {@code false} if the
     *                response was sent or refused meanwhile
     * @return Whether this ended the stream
     */
    boolean replace(BooleanSupplier replace) {
        Effects effects = new Effects();
        lock.lock();
        try {
            if (state != State.OPEN || !replace.getAsBoolean()) {
                return false;
            }
            closeLocked(State.COMPLETED, new IllegalStateException("The stream was answered with another response"), null, effects);
        } finally {
            lock.unlock();
        }
        effects.run();
        body.close();
        return true;
    }

    /**
     * @return Whether a write would complete immediately
     */
    boolean isWritable() {
        lock.lock();
        try {
            return state == State.OPEN && written - consumed < highWaterMark;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return Whether the stream is open
     */
    boolean isOpen() {
        lock.lock();
        try {
            return state == State.OPEN;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add a callback for the close of the stream. It runs at once if the stream is closed.
     *
     * @param callback The callback, with the failure or {@code null} for a normal end
     */
    void onClose(Consumer<@Nullable Throwable> callback) {
        Throwable closeCause;
        lock.lock();
        try {
            if (state == State.OPEN) {
                List<Consumer<@Nullable Throwable>> callbacks = closeCallbacks;
                if (callbacks == null) {
                    callbacks = new ArrayList<>(2);
                    closeCallbacks = callbacks;
                }
                callbacks.add(callback);
                return;
            }
            closeCause = callbackCause(state, cause);
        } finally {
            lock.unlock();
        }
        runCallback(callback, closeCause);
    }

    /**
     * Set the listener of a pulling producer, called whenever the stream becomes writable.
     *
     * @param listener The listener
     */
    void onDemand(Runnable listener) {
        lock.lock();
        try {
            demandListener = listener;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onBytesConsumed(long bytesConsumed) {
        Effects effects;
        lock.lock();
        try {
            long c = consumed + bytesConsumed;
            // saturate: a downstream that asks for everything passes Long.MAX_VALUE
            consumed = c < consumed ? Long.MAX_VALUE : c;
            effects = releaseWaiters(null);
        } finally {
            lock.unlock();
        }
        if (effects != null) {
            effects.run();
        }
    }

    @Override
    public void disregardBackpressure() {
        onBytesConsumed(Long.MAX_VALUE);
    }

    @Override
    public void allowDiscard() {
        // the connection will not take the body: the client disconnected, the response failed,
        // or it has no body (HEAD)
        Effects effects = new Effects();
        lock.lock();
        try {
            if (state == State.OPEN) {
                Throwable closed = new ConnectionClosedException(bodyless
                    ? "The response has no body: the stream of a HEAD request is closed"
                    : "The response body was discarded: the client closed the connection, or the response was not written");
                closeLocked(State.DISCARDED, closed, bodyless ? null : closed, effects);
            }
        } finally {
            lock.unlock();
        }
        effects.run();
    }

    /**
     * Release the writes that are below the high-water mark now, and tell a pulling producer.
     *
     * @param effects The effects so far, or {@code null}
     * @return The effects, or {@code null} if there are none: allocated only when needed, since
     * the connection reports what it took for every write
     */
    private @Nullable Effects releaseWaiters(@Nullable Effects effects) {
        Waiter waiter;
        while ((waiter = waiters.peek()) != null && waiter.end - consumed < highWaterMark) {
            waiters.poll();
            if (effects == null) {
                effects = new Effects();
            }
            effects.complete(waiter.future);
        }
        if (demandListener != null && state == State.OPEN && written - consumed < highWaterMark) {
            if (effects == null) {
                effects = new Effects();
            }
            effects.demand = demandListener;
        }
        return effects;
    }

    private void closeLocked(State newState, Throwable writeCause, @Nullable Throwable callbackCause, Effects effects) {
        state = newState;
        cause = writeCause;
        Waiter waiter;
        while ((waiter = waiters.poll()) != null) {
            if (newState == State.COMPLETED) {
                effects.complete(waiter.future);
            } else {
                effects.fail(waiter.future, writeCause);
            }
        }
        List<Consumer<@Nullable Throwable>> callbacks = closeCallbacks;
        closeCallbacks = null;
        effects.callbacks = callbacks;
        effects.callbackCause = callbackCause;
        demandListener = null;
    }

    private @Nullable Throwable callbackCause(State closedState, @Nullable Throwable closeCause) {
        return switch (closedState) {
            case COMPLETED -> null;
            case DISCARDED -> bodyless ? null : closeCause;
            default -> closeCause;
        };
    }

    private void submitLocked(Runnable task, Effects effects) {
        // queued under the lock: the tasks run in the order of the writes
        tasks.add(task);
        effects.drain = true;
    }

    private void scheduleDrain() {
        if (pendingTasks.getAndIncrement() == 0) {
            // always a new task, also on an event loop: the caller may be inside a call of the
            // buffer, which must not be reentered. The default buffer runs the task on this
            // thread, and serializes the calls itself
            executor.execute(this::drain);
        }
    }

    private void drain() {
        int missed = 1;
        while (true) {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable e) {
                    LOG.warn("Failed to write to the response body", e);
                }
            }
            missed = pendingTasks.addAndGet(-missed);
            if (missed == 0) {
                return;
            }
        }
    }

    private void runCallback(Consumer<@Nullable Throwable> callback, @Nullable Throwable closeCause) {
        try {
            context.propagate(() -> callback.accept(closeCause));
        } catch (Throwable e) {
            LOG.warn("An onClose callback of a response stream failed", e);
        }
    }

    private enum State {
        OPEN,
        COMPLETED,
        FAILED,
        DISCARDED
    }

    /**
     * A write whose stage completes when the bytes up to its end are below the high-water mark.
     *
     * @param end    The offset of the end of the write
     * @param future The stage
     */
    private record Waiter(long end, CompletableFuture<Void> future) {
    }

    /**
     * What a state change triggers, run after the lock is released: stages, callbacks and the
     * producer continue on this thread, and must not run while the stream is locked.
     */
    private final class Effects {
        private @Nullable List<CompletableFuture<Void>> completed;
        private @Nullable List<CompletableFuture<Void>> failed;
        private @Nullable Throwable failure;
        private @Nullable List<Consumer<@Nullable Throwable>> callbacks;
        private @Nullable Throwable callbackCause;
        private @Nullable Runnable demand;
        private boolean drain;

        void complete(CompletableFuture<Void> future) {
            if (completed == null) {
                completed = new ArrayList<>(2);
            }
            completed.add(future);
        }

        void fail(CompletableFuture<Void> future, Throwable cause) {
            if (failed == null) {
                failed = new ArrayList<>(2);
            }
            failed.add(future);
            failure = cause;
        }

        void run() {
            if (drain) {
                scheduleDrain();
            }
            if (completed == null && failed == null && callbacks == null && demand == null) {
                return;
            }
            context.propagate(this::completeStages);
            if (callbacks != null) {
                for (Consumer<@Nullable Throwable> callback : callbacks) {
                    runCallback(callback, callbackCause);
                }
            }
        }

        private void completeStages() {
            if (completed != null) {
                for (CompletableFuture<Void> future : completed) {
                    future.complete(null);
                }
            }
            if (failed != null) {
                Throwable error = failure;
                for (CompletableFuture<Void> future : failed) {
                    future.completeExceptionally(error);
                }
            }
            if (demand != null) {
                demand.run();
            }
        }
    }
}
