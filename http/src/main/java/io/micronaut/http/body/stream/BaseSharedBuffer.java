/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.http.body.ByteBody;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base type for a shared buffer that distributes a single {@link BufferConsumer} input to multiple
 * streaming {@link io.micronaut.http.body.ByteBody}s.<br>
 * The subclass handles concurrency (for netty, event loop).
 */
@Internal
public abstract class BaseSharedBuffer implements BufferConsumer {
    private static final Class<ByteBody> SPLIT_LOG_CLASS = ByteBody.class;
    private static final Logger SPLIT_LOG = LoggerFactory.getLogger(SPLIT_LOG_CLASS);

    private static final BufferConsumer[] NO_SUBSCRIBERS = new BufferConsumer[0];
    @SuppressWarnings("unchecked")
    private static final DelayedExecutionFlow<ReadBuffer>[] NO_FULL_SUBSCRIBERS = (DelayedExecutionFlow<ReadBuffer>[]) new DelayedExecutionFlow<?>[0];

    private final ReadBufferFactory readBufferFactory;
    private SizeLimitTracker.TrackerPair sizeLimitTrackers;
    /**
     * Upstream of all subscribers. This is only used to cancel incoming data if the max
     * request size is exceeded.
     */
    private final BufferConsumer.Upstream rootUpstream;
    private Terminal terminal = Open.INSTANCE;
    /**
     * Number of reserved subscriber spots. A new subscription MUST be preceded by a
     * reservation, and every reservation MUST have a subscription.
     */
    private int reserved = 1;
    /**
     * Active subscribers.
     */
    private BufferConsumer[] subscribers = NO_SUBSCRIBERS;
    /**
     * Active subscribers that need the fully buffered body.
     */
    private DelayedExecutionFlow<ReadBuffer>[] fullSubscribers = NO_FULL_SUBSCRIBERS;
    /**
     * Number of bytes received so far.
     */
    private long lengthSoFar = 0;
    /**
     * The expected length of the whole body. This is -1 if we're uncertain, otherwise it must
     * be accurate. This can come from a content-length header, but it's also set once the full
     * body has been received.
     */
    private volatile long expectedLength = -1;
    /**
     * If not all {@link #subscribers} are ready or there are {@link #fullSubscribers}, this list
     * buffers input data.
     */
    @Nullable
    private List<ReadBuffer> buffer;

    public BaseSharedBuffer(ReadBufferFactory readBufferFactory, BodySizeLimits limits, BufferConsumer.Upstream rootUpstream) {
        this.readBufferFactory = readBufferFactory;
        this.rootUpstream = rootUpstream;
        this.sizeLimitTrackers = SizeLimitTracker.notThreadSafe(limits);
    }

    public static void logClaim() {
        if (SPLIT_LOG.isTraceEnabled()) {
            SPLIT_LOG.trace("Body split at this location. This is not an error, but may aid in debugging other errors", new Exception());
        }
    }

    /**
     * Add a second size limit tracker to this buffer. This tracker can be shared with other
     * buffers.
     *
     * @param sizeLimitTrackers The additional tracker
     */
    public void addSizeLimitTrackers(SizeLimitTracker.TrackerPair sizeLimitTrackers) {
        this.sizeLimitTrackers = SizeLimitTracker.combine(this.sizeLimitTrackers, sizeLimitTrackers);
    }

    /**
     * Get the exact body length, if available. This is either set from {@code Content-Length} or
     * when the body is fully buffered.
     *
     * @return The expected body length
     */
    public final OptionalLong getExpectedLength() {
        long l = expectedLength;
        return l < 0 ? OptionalLong.empty() : OptionalLong.of(l);
    }

    public final BufferConsumer.Upstream getRootUpstream() {
        return rootUpstream;
    }

    public final void setExpectedLengthFrom(@Nullable String contentLength) {
        if (contentLength == null) {
            return;
        }
        long parsed;
        try {
            parsed = Long.parseLong(contentLength);
        } catch (NumberFormatException e) {
            return;
        }
        if (parsed < 0) {
            return;
        }
        Exception totalSizeException = sizeLimitTrackers.totalSize().add(parsed);
        if (totalSizeException != null) {
            error(totalSizeException);
            // nobody can use the body anymore, so like the check in add0, let the upstream drop
            // the rest instead of stalling it on our missing demand
            rootUpstream.allowDiscard();
        }
        setExpectedLength(parsed);
    }

    public final void setExpectedLength(long length) {
        if (length < 0) {
            throw new IllegalArgumentException("Should be > 0");
        }
        this.expectedLength = length;
    }

    /**
     * Reserve a spot for a future subscribe operation.<br>
     * Not thread safe, caller must handle concurrency.
     */
    protected void reserve0() {
        if (reserved == 0) {
            throw new IllegalStateException("Cannot go from streaming state back to buffering state");
        }
        reserved++;
    }

    /**
     * Forward any already-buffered data to the given new subscriber.
     *
     * @param subscriber The new subscriber, or {@code null} if the reservation has been cancelled
     *                   and the data can just be discarded
     * @param last {@code true} iff this was the last reservation and the buffer can be discarded
     *                         after this call
     */
    private void forwardInitialBuffer(@Nullable BufferConsumer subscriber, boolean last) {
        if (subscriber != null) {
            if (buffer != null) {
                if (last) {
                    // We hand our copy of the data to a streaming subscriber and drop it, so we no
                    // longer hold these bytes and their charge has to go, the same way
                    // discardBuffer() releases it. From here on the subscriber is responsible for
                    // whatever it keeps: AsFlux charges the bytes again until it delivers them,
                    // and the other streaming consumers are not charged for anything they receive
                    // after subscribing either. Without this the bytes that arrived before the
                    // subscriber showed up stayed charged for the lifetime of the body, on top of
                    // whatever the subscriber charged, and that permanently shrank the remaining
                    // budget for the rest of the body.
                    // The pieces are counted before they are composed, like discardBuffer() counts
                    // them before it closes them: compose() consumes them and closes them all if it
                    // fails part way, so they can only be counted while this buffer still owns them.
                    long n = 0;
                    for (ReadBuffer piece : buffer) {
                        n += piece.readable();
                    }
                    sizeLimitTrackers.bufferedSize().subtract(n);
                }
                subscriber.add(getBufferedData(last));
            }
        } else {
            if (last) {
                discardBuffer();
            }
        }
    }

    /**
     * Called after a subscribe operation. Used for leak detection.
     *
     * @param last {@code true} iff this was the last reservation
     */
    protected void afterSubscribe(boolean last) {
    }

    /**
     * Get all data buffered so far. This does <i>not</i> release the buffered size charge for the
     * data: a subscriber that asked for the full body ({@link #subscribeFull0}) keeps the data, so
     * it is still held, and for a form field that charge is shared with the form-wide limit and
     * must survive the field's completion. Only the hand-off to a streaming subscriber releases
     * it, see {@link #forwardInitialBuffer}.
     *
     * @param discardBuffer {@code true} iff the buffer can and should be discarded after this call
     * @return The buffered data
     */
    private ReadBuffer getBufferedData(boolean discardBuffer) {
        if (buffer == null) {
            return readBufferFactory.createEmpty();
        } else if (discardBuffer) {
            List<ReadBuffer> pieces = buffer;
            buffer = null;
            return readBufferFactory.compose(pieces);
        } else {
            List<ReadBuffer> pieces = new ArrayList<>(buffer.size());
            for (ReadBuffer buffer : buffer) {
                pieces.add(buffer.duplicate());
            }
            return readBufferFactory.compose(pieces);
        }
    }

    /**
     * Add a subscriber. Must be preceded by a reservation.<br>
     * Not thread safe, caller must handle concurrency.
     *
     * @param subscriber       The subscriber to add. Can be {@code null}, then the bytes will just be discarded
     * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
     */
    protected final void subscribe0(@Nullable BufferConsumer subscriber, BufferConsumer.Upstream specificUpstream) {
        if (reserved == 0) {
            throw new IllegalStateException("Need to reserve a spot first");
        }

        boolean last = --reserved == 0;
        if (subscriber != null) {
            Terminal subscribedTerminal = terminal;
            if (subscribedTerminal instanceof Open) {
                BufferConsumer[] next = Arrays.copyOf(subscribers, subscribers.length + 1);
                next[subscribers.length] = subscriber;
                subscribers = next;
            }
            forwardInitialBuffer(subscriber, last);
            Throwable failure = subscribedTerminal.failure();
            if (failure != null) {
                subscriber.error(failure);
                if (subscribedTerminal instanceof Overflow || subscribedTerminal instanceof Complete) {
                    specificUpstream.allowDiscard();
                }
            } else if (subscribedTerminal instanceof Complete) {
                subscriber.complete();
            }
        } else {
            forwardInitialBuffer(null, last);
        }
        afterSubscribe(last);
    }

    /**
     * Optimized version of {@link #subscribe0} for subscribers that want to buffer the full
     * body. The returned flow will complete when the
     * input is buffered. The returned flow will always be identical to the {@code targetFlow}
     * parameter IF {@code canReturnImmediate} is false. If {@code canReturnImmediate} is true,
     * this method will SOMETIMES return an immediate ExecutionFlow instead as an optimization.
     *
     * @param targetFlow The delayed flow to use if {@code canReturnImmediate} is false and/or
     *                   we have to wait for the result. Its subclass registers
     *                   {@link #cancelFull0} to run when it is cancelled
     * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
     * @param canReturnImmediate Whether we can return an immediate ExecutionFlow instead of
     *                  {@code targetFlow}, when appropriate
     * @return A flow that will complete when all data has arrived, with a buffer containing that data
     */
    protected final ExecutionFlow<ReadBuffer> subscribeFull0(DelayedExecutionFlow<ReadBuffer> targetFlow, BufferConsumer.Upstream specificUpstream, boolean canReturnImmediate) {
        if (reserved <= 0) {
            throw new IllegalStateException("Need to reserve a spot first. This should not happen, StreamingNettyByteBody should guard against it");
        }

        ExecutionFlow<ReadBuffer> ret = targetFlow;
        boolean cancelled = false;

        boolean last = --reserved == 0;
        Throwable error = terminal.failure();
        if (error != null) {
            if (terminal instanceof Overflow || terminal instanceof Complete) {
                specificUpstream.allowDiscard();
            }
            if (canReturnImmediate) {
                ret = ExecutionFlow.error(error);
            } else {
                targetFlow.completeExceptionally(error);
            }
        } else if (terminal instanceof Complete) {
            ReadBuffer buf = getBufferedData(last);
            if (canReturnImmediate) {
                ret = ExecutionFlow.just(buf);
            } else {
                targetFlow.complete(buf);
            }
        } else {
            DelayedExecutionFlow<ReadBuffer>[] next = Arrays.copyOf(fullSubscribers, fullSubscribers.length + 1);
            next[fullSubscribers.length] = targetFlow;
            fullSubscribers = next;
            // cancelFull0 can have run before a subscribe that was delayed
            cancelled = targetFlow.isCancelled();
        }
        afterSubscribe(last);
        if (cancelled) {
            cancelFull0(targetFlow, specificUpstream);
        }

        return ret;
    }

    /**
     * Remove a subscriber of {@link #subscribeFull0} whose flow was cancelled before the input
     * completed, e.g. by a request timeout while the body is still read. Like a cancelled
     * {@link AsFlux} reader, it takes nothing anymore: the bytes buffered for it are dropped
     * unless another reader still needs them, and its upstream does not have to send the rest.
     * The flow never completes.
     * <br>Not thread safe, caller must handle concurrency.
     *
     * @param targetFlow       The flow passed to {@link #subscribeFull0}
     * @param specificUpstream The upstream for the subscriber
     */
    protected final void cancelFull0(DelayedExecutionFlow<ReadBuffer> targetFlow, BufferConsumer.Upstream specificUpstream) {
        for (int i = 0; i < fullSubscribers.length; i++) {
            if (fullSubscribers[i] == targetFlow) {
                DelayedExecutionFlow<ReadBuffer>[] next = Arrays.copyOf(fullSubscribers, fullSubscribers.length - 1);
                System.arraycopy(fullSubscribers, i + 1, next, i, next.length - i);
                fullSubscribers = next;
                if (next.length == 0 && reserved == 0) {
                    discardBuffer();
                }
                specificUpstream.allowDiscard();
                specificUpstream.disregardBackpressure();
                return;
            }
        }
    }

    /**
     * Remove a cancelled or terminated streaming subscriber. This can be called from the subscriber
     * thread. The default acquires the buffer monitor: subclasses using it must synchronize all
     * buffer mutations on that monitor. Subclasses with their own serialization must override this
     * method and call {@link #removeSubscriber0} in their serialized context.
     *
     * @param subscriber The subscriber to remove
     */
    protected synchronized void removeSubscriber(BufferConsumer subscriber) {
        removeSubscriber0(subscriber);
    }

    /**
     * Remove a streaming subscriber in the subclass's serialized buffer context.
     *
     * @param subscriber The subscriber to remove
     */
    protected final void removeSubscriber0(BufferConsumer subscriber) {
        for (int i = 0; i < subscribers.length; i++) {
            if (subscribers[i] == subscriber) {
                BufferConsumer[] next = Arrays.copyOf(subscribers, subscribers.length - 1);
                System.arraycopy(subscribers, i + 1, next, i, next.length - i);
                subscribers = next;
                return;
            }
        }
    }

    /**
     * Discard the previously buffered bytes.
     */
    private void discardBuffer() {
        if (buffer != null) {
            long n = 0;
            for (ReadBuffer rb : buffer) {
                n += rb.readable();
                rb.close();
            }
            buffer = null;
            sizeLimitTrackers.bufferedSize().subtract(n);
        }
    }

    /**
     * Add a given buffer to this {@link BaseSharedBuffer}.<br>
     * Not thread safe, caller must handle concurrency.
     */
    @Override
    public void add(ReadBuffer rb) {
        addGuarded(rb, false);
    }

    /**
     * Add a given buffer to this {@link BaseSharedBuffer} and complete it, in one operation. This
     * allows subscribers that implement {@link BufferConsumer#addAndComplete(ReadBuffer)} to
     * combine the final bytes and the completion signal into a single downstream message.<br>
     * Not thread safe, caller must handle concurrency.
     */
    @Override
    public void addAndComplete(ReadBuffer rb) {
        // The final bytes are stored and this buffer is marked complete before any streaming
        // subscriber learns of the completion. A subscriber may consume a reserved split from
        // inside its completion callback (the servlet integration does), and that consumer has
        // to find the final bytes in the buffer and a completed state, or it would miss them and
        // then wait for a completion that is never delivered.
        BufferConsumer[] targets = subscribers;
        List<ReadBuffer> deferred = addGuarded(rb, true);
        if (deferred == null) {
            complete0(true);
            return;
        }
        // the copies not yet delivered are closed if complete0 or a delivery throws, so that a
        // caller with an expected length, or a subscriber callback that fails, cannot leak them
        int delivered = 0;
        try {
            complete0(false);
            // The array captured before addGuarded is also the snapshot used to make the copies.
            for (BufferConsumer target : targets) {
                // ownership of the copy passes to the consumer with the call, even if it throws
                ReadBuffer copy = deferred.get(delivered++);
                target.addAndComplete(copy);
            }
        } finally {
            for (int i = delivered; i < deferred.size(); i++) {
                deferred.get(i).close();
            }
        }
    }

    /**
     * Hook for subclasses that need to apply a concurrency guard around the {@link #add} portion
     * of {@link #add(ReadBuffer)} and {@link #addAndComplete(ReadBuffer)}. Subclasses must call
     * {@code super.addGuarded(rb, completeAfter)} and return its result.
     *
     * @param rb           The buffer to add
     * @param completeAfter Whether the subscribers should be completed together with this buffer
     * @return With {@code completeAfter}, the copies of {@code rb} still to be delivered to the
     * streaming subscribers together with their completion, one per subscriber in order, or
     * {@code null} if there are none to deliver. Always {@code null} without {@code completeAfter}.
     */
    @Nullable
    protected List<ReadBuffer> addGuarded(ReadBuffer rb, boolean completeAfter) {
        return add0(rb, completeAfter);
    }

    @Nullable
    private List<ReadBuffer> add0(ReadBuffer rb, boolean completeAfter) {
        List<ReadBuffer> deferred = null;
        try (rb) {
            // calculate the new total length
            long newLength = lengthSoFar + rb.readable();
            long expectedLength = this.expectedLength;
            if (expectedLength != -1 && newLength > expectedLength) {
                throw new IncorrectContentLengthException("Received more bytes than specified by Content-Length");
            }
            lengthSoFar = newLength;

            // drop messages if we're done with all subscribers
            if (terminal instanceof Complete || terminal instanceof Failed) {
                return null;
            }
            if (expectedLength == -1) {
                Exception totalSizeException = sizeLimitTrackers.totalSize().add(rb.readable());
                if (totalSizeException != null) {
                    // for maxBodySize, all subscribers get the error
                    error(totalSizeException);
                    rootUpstream.allowDiscard();
                    return null;
                }
            } // else, already checked the Content-Length

            BufferConsumer[] targets = subscribers;
            if (completeAfter && targets.length > 0) {
                // delivered by addAndComplete once the state below is final
                deferred = new ArrayList<>(targets.length);
                for (BufferConsumer ignored : targets) {
                    deferred.add(rb.duplicate());
                }
            } else {
                for (BufferConsumer consumer : targets) {
                    consumer.add(rb.duplicate());
                }
            }
            if ((reserved > 0 || fullSubscribers.length > 0) && terminal instanceof Open) {
                Exception exceeded = sizeLimitTrackers.bufferedSize().add(rb.readable());
                if (exceeded != null) {
                    terminate(new Overflow(exceeded), true);
                    if (subscribers.length == 0 && reserved <= 0) {
                        rootUpstream.allowDiscard();
                    }
                } else {
                    if (buffer == null) {
                        buffer = new ArrayList<>();
                    }
                    ReadBufferCoalescing.add(buffer, rb.move(), readBufferFactory);
                }
            }
        } catch (Throwable e) {
            if (deferred != null) {
                for (ReadBuffer copy : deferred) {
                    copy.close();
                }
            }
            throw e;
        }
        return deferred;
    }

    /**
     * Implementation of {@link BufferConsumer#complete()}.<br>
     * Not thread safe, caller must handle concurrency.
     */
    @Override
    public void complete() {
        complete0(true);
    }

    private void complete0(boolean notifySubscribers) {
        if (expectedLength > lengthSoFar) {
            throw new IncorrectContentLengthException("Received fewer bytes than specified by Content-Length");
        }
        expectedLength = lengthSoFar;
        terminate(new Complete(terminal instanceof Overflow overflow ? overflow.cause : null), notifySubscribers);
    }

    /**
     * Implementation of {@link BufferConsumer#error(Throwable)}.<br>
     * Not thread safe, caller must handle concurrency.
     *
     * @param e The error
     */
    @Override
    public void error(Throwable e) {
        terminate(new Failed(e), true);
    }

    private void terminate(Terminal next, boolean notifySubscribers) {
        // A later failure must still invalidate buffered data retained for reserved readers.
        if (terminal instanceof Failed || (terminal instanceof Complete && !(next instanceof Failed)) || (next instanceof Overflow && terminal instanceof Overflow)) {
            return;
        }
        terminal = next;
        BufferConsumer[] streaming = subscribers;
        if (!(next instanceof Overflow)) {
            subscribers = NO_SUBSCRIBERS;
        }
        if (!(next instanceof Complete)) {
            discardBuffer();
        }
        Throwable callbackFailure = null;
        try {
            if (next instanceof Complete && notifySubscribers || next instanceof Failed) {
                for (BufferConsumer subscriber : streaming) {
                    try {
                        if (next instanceof Complete) {
                            subscriber.complete();
                        } else {
                            subscriber.error(Objects.requireNonNull(next.failure()));
                        }
                    } catch (Throwable e) {
                        if (callbackFailure == null) {
                            callbackFailure = e;
                        } else if (callbackFailure != e) {
                            callbackFailure.addSuppressed(e);
                        }
                    }
                }
            }
        } finally {
            // Streaming callbacks may cancel full readers or invalidate the buffered result.
            DelayedExecutionFlow<ReadBuffer>[] full = fullSubscribers;
            fullSubscribers = NO_FULL_SUBSCRIBERS;
            for (int i = 0; i < full.length; i++) {
                try {
                    if (next instanceof Complete) {
                        full[i].complete(getBufferedData(reserved <= 0 && i == full.length - 1));
                    } else {
                        full[i].completeExceptionally(Objects.requireNonNull(next.failure()));
                    }
                } catch (Throwable e) {
                    if (callbackFailure == null) {
                        callbackFailure = e;
                    } else if (callbackFailure != e) {
                        callbackFailure.addSuppressed(e);
                    }
                }
            }
        }
        if (callbackFailure != null) {
            ExceptionUtils.sneakyThrow(callbackFailure);
        }
    }

    private sealed interface Terminal {
        default @Nullable Throwable failure() {
            return switch (this) {
                case Open ignored -> null;
                case Complete complete -> complete.overflow;
                case Failed failed -> failed.cause;
                case Overflow overflow -> overflow.cause;
            };
        }
    }

    private enum Open implements Terminal {
        INSTANCE
    }

    // An overflow stops buffering, but existing streaming subscribers still need completion.
    private record Complete(@Nullable Exception overflow) implements Terminal { }

    private record Failed(Throwable cause) implements Terminal { }

    private record Overflow(Exception cause) implements Terminal { }

    /**
     * {@link BufferConsumer} that can subscribe to a {@link BaseSharedBuffer} and return the
     * buffer as a {@link Flux}. Used to implement {@link ByteBody#toReadBufferPublisher()} and
     * similar methods.
     */
    public static final class AsFlux extends AtomicLong implements BufferConsumer {
        private final BaseSharedBuffer sharedBuffer;
        private final Sinks.Many<ReadBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        private volatile boolean done;
        private boolean first = true;

        public AsFlux(BaseSharedBuffer sharedBuffer) {
            this.sharedBuffer = sharedBuffer;
        }

        @Override
        public void add(ReadBuffer buf) {
            if (get() < 0) {
                buf.close();
                return;
            }
            if (first) {
                sharedBuffer.sizeLimitTrackers = new SizeLimitTracker.TrackerPair(
                    sharedBuffer.sizeLimitTrackers.totalSize(), sharedBuffer.sizeLimitTrackers.bufferedSize().makeAtomic()
                );
                first = false;
            }
            int size = buf.readable();
            Exception bufferExceededExc = sharedBuffer.sizeLimitTrackers.bufferedSize().add(size);
            if (bufferExceededExc != null) {
                sink.tryEmitError(bufferExceededExc);
                buf.close();
            } else if (!acquireCharge(size)) {
                sharedBuffer.sizeLimitTrackers.bufferedSize().subtract(size);
                buf.close();
            } else if (sink.tryEmitNext(buf) != Sinks.EmitResult.OK) {
                releaseCharge(size);
                buf.close();
            }
        }

        @Override
        public void complete() {
            done = true;
            sink.tryEmitComplete();
        }

        @Override
        public void error(Throwable e) {
            done = true;
            sink.tryEmitError(e);
        }

        public Flux<ReadBuffer> asFlux(Upstream upstream) {
            return sink.asFlux()
                .doOnSubscribe(s -> upstream.start())
                .doOnNext(bb -> {
                    int size = bb.readable();
                    releaseCharge(size);
                    upstream.onBytesConsumed(size);
                })
                .doOnTerminate(this::release)
                .doOnCancel(() -> {
                    try {
                        release();
                    } finally {
                        upstream.allowDiscard();
                        upstream.disregardBackpressure();
                    }
                })
                .doOnDiscard(ReadBuffer.class, ReadBuffer::close);
        }

        private void release() {
            closeCharge();
            if (!done) {
                sharedBuffer.removeSubscriber(this);
            }
        }

        // -1 means all outstanding charges have been returned.
        private boolean acquireCharge(long bytes) {
            long current;
            do {
                current = get();
                if (current < 0) {
                    return false;
                }
            } while (!compareAndSet(current, current + bytes));
            return true;
        }

        private void releaseCharge(long bytes) {
            long current;
            do {
                current = get();
                if (current < 0) {
                    return;
                }
            } while (!compareAndSet(current, current - bytes));
            sharedBuffer.sizeLimitTrackers.bufferedSize().subtract(bytes);
        }

        private void closeCharge() {
            long bytes = getAndSet(-1);
            if (bytes > 0) {
                sharedBuffer.sizeLimitTrackers.bufferedSize().subtract(bytes);
            }
        }
    }

    /**
     * Thrown when {@link #complete()} is called before {@link #getExpectedLength()} bytes are
     * received.
     */
    public static final class IncorrectContentLengthException extends IllegalStateException {
        IncorrectContentLengthException(String msg) {
            super(msg);
        }
    }
}
