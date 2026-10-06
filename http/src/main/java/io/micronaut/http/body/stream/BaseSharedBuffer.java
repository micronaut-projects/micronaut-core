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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.body.ByteBody;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Base type for a shared buffer that distributes a single {@link BufferConsumer} input to multiple
 * streaming {@link io.micronaut.http.body.ByteBody}s.<br>
 * The subclass handles concurrency (for netty, event loop).
 */
@Internal
public abstract class BaseSharedBuffer implements BufferConsumer {
    private static final Class<ByteBody> SPLIT_LOG_CLASS = ByteBody.class;
    private static final Logger SPLIT_LOG = LoggerFactory.getLogger(SPLIT_LOG_CLASS);

    private final ReadBufferFactory readBufferFactory;
    private SizeLimitTracker.TrackerPair sizeLimitTrackers;
    /**
     * Upstream of all subscribers. This is only used to cancel incoming data if the max
     * request size is exceeded.
     */
    private final BufferConsumer.Upstream rootUpstream;
    /**
     * Whether the input is complete.
     */
    private boolean complete;
    /**
     * Any stream error.
     */
    @Nullable
    private Throwable error;
    /**
     * Whether {@link #error} is set, for {@link #isFailed()} from any thread.
     */
    private volatile boolean failed;
    /**
     * Number of reserved subscriber spots. A new subscription MUST be preceded by a
     * reservation, and every reservation MUST have a subscription.
     */
    private int reserved = 1;
    /**
     * Active subscribers.
     */
    @Nullable
    private List<BufferConsumer> subscribers;
    /**
     * Active subscribers that need the fully buffered body.
     */
    @Nullable
    private List<DelayedExecutionFlow<ReadBuffer>> fullSubscribers;
    /**
     * This flag is only used in tests, to verify that the BufferConsumer methods arent called
     * in a reentrant fashion.
     */
    private boolean working = false;
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
    @Nullable
    private Exception bufferSizeExceeded = null;
    /**
     * Whether {@link #bufferSizeExceeded} is set, for {@link #isBufferLimitExceeded()} from any
     * thread.
     */
    private volatile boolean bufferLimitExceeded;
    /**
     * The trailers of the body, see {@link ByteBody#trailers()}. Completed before the
     * subscribers are, so that a subscriber finds them in its {@link BufferConsumer#complete()}.
     */
    private final CompletableFuture<HttpHeaders> trailers = new CompletableFuture<>();
    /**
     * If not negative, each {@link AsPublisher} reader is charged for the bytes it has not delivered
     * yet against its own limit of this size, instead of against the buffered size of this
     * buffer, see {@link #setReaderBufferLimit(long)}.
     */
    private long readerBufferLimit = -1;
    /**
     * Whether the bytes that arrive before the first reader subscribes are kept past the buffer
     * limit for a reader that streams them, see {@link #setKeepInitialBytes()}.
     */
    private boolean keepInitialBytes;
    /**
     * Whether a reader subscribed, or a reservation was released without one.
     */
    private boolean subscribed;
    /**
     * The buffer limit failure of the bytes that arrived before the first reader subscribed,
     * see {@link #setKeepInitialBytes()}, or {@code null}. While it is set, the {@link #buffer}
     * holds these bytes and they are not charged to the buffered size.
     */
    @Nullable
    private Exception initialBytesOverLimit;

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

    /**
     * Get the trailers of the body, see {@link ByteBody#trailers()}.
     *
     * @return The trailers
     */
    public final CompletionStage<HttpHeaders> getTrailers() {
        return trailers;
    }

    /**
     * @return Whether more bytes than the buffer limit arrived while bytes were kept for a
     * reserved reader, so that the kept bytes were dropped
     * @since 5.3.0
     */
    public final boolean isBufferLimitExceeded() {
        return bufferLimitExceeded;
    }

    /**
     * @return Whether the body failed
     * @since 5.3.0
     */
    public final boolean isFailed() {
        return failed;
    }

    /**
     * Charge each {@link AsPublisher} reader for the bytes it has not delivered yet against a limit
     * of its own, so that the buffered size of this buffer only counts the bytes it keeps for
     * the reserved readers. Must be called before the first reader subscribes.
     *
     * @param limit The maximum number of bytes a reader holds
     * @since 5.3.0
     */
    public final void setReaderBufferLimit(long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("The reader buffer limit is negative");
        }
        this.readerBufferLimit = limit;
    }

    /**
     * Keep the bytes that arrive before the first reader subscribes even past the buffer limit,
     * for a reader that streams the body without holding it, see
     * {@link BufferConsumer#isUnbuffered()}. Only for an upstream that bounds what it
     * sends before a reader asks for more, e.g. the initial window of a connection: the bytes a
     * server reads with the headers can be more than the limit before the route reads the body.
     * Any other reader fails with the buffer limit, as it would without this, and the bytes are
     * dropped when it subscribes. Must be called before the first bytes are added.
     *
     * @since 5.3.0
     */
    public final void setKeepInitialBytes() {
        this.keepInitialBytes = true;
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
     * Take the already-buffered data for the given new subscriber, which the caller forwards to
     * it once this buffer is done working.
     *
     * @param subscriber The new subscriber, or {@code null} if the reservation has been cancelled
     *                   and the data can just be discarded
     * @param last {@code true} iff this was the last reservation and the buffer can be discarded
     *                         after this call
     * @return The data to forward, or {@code null} if there is none
     */
    @Nullable
    private ReadBuffer takeInitialBuffer(@Nullable BufferConsumer subscriber, boolean last) {
        if (subscriber != null) {
            if (buffer != null) {
                if (last) {
                    // We hand our copy of the data to a streaming subscriber and drop it, so we no
                    // longer hold these bytes and their charge has to go, the same way
                    // discardBuffer() releases it. From here on the subscriber is responsible for
                    // whatever it keeps: AsPublisher charges the bytes again until it delivers them,
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
                return getBufferedData(last);
            }
        } else {
            if (last) {
                discardBuffer();
            }
        }
        return null;
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
     * it, see {@link #takeInitialBuffer}.
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
        assert !working;

        if (reserved == 0) {
            throw new IllegalStateException("Need to reserve a spot first");
        }

        working = true;
        boolean last = --reserved == 0;
        subscribed = true;
        ReadBuffer initial;
        Throwable failure = null;
        boolean discard = false;
        if (initialBytesOverLimit != null && subscriber != null && subscriber.isUnbuffered()) {
            initial = subscribeKeeping(subscriber, last);
            failure = error;
        } else {
            dropInitialBytes();
            // the buffered bytes are kept for the subscribers that wait for the full body, which
            // hold no reservation: they are given them when the body completes
            initial = takeInitialBuffer(subscriber, last && fullSubscribers == null);
            if (subscriber != null) {
                if (subscribers == null) {
                    subscribers = new ArrayList<>(1);
                }
                subscribers.add(subscriber);
                if (error != null) {
                    failure = error;
                } else if (bufferSizeExceeded != null) {
                    failure = bufferSizeExceeded;
                    discard = true;
                }
            }
        }
        boolean completed = complete;
        // the subscriber is called once this buffer is done working: it can subscribe another
        // split reentrantly, e.g. the reader of a copy of the body whose completion runs the
        // reader of the body, which then finds the bytes buffered for it
        working = false;
        if (subscriber != null) {
            if (initial != null) {
                subscriber.add(initial);
            }
            if (failure != null) {
                subscriber.error(failure);
                if (discard) {
                    specificUpstream.allowDiscard();
                }
            }
            if (completed) {
                subscriber.complete();
            }
        }
        afterSubscribe(last);
    }

    /**
     * Subscribe a reader that streams the body without holding it to the bytes kept past the
     * buffer limit before it subscribed, see {@link #setKeepInitialBytes()}. The readers still
     * reserved are held to the limit: the kept bytes are dropped for them.
     *
     * @param subscriber The reader
     * @param last       Whether this was the last reservation
     * @return The kept bytes, which the caller forwards to the reader
     */
    private ReadBuffer subscribeKeeping(BufferConsumer subscriber, boolean last) {
        if (subscribers == null) {
            subscribers = new ArrayList<>(1);
        }
        subscribers.add(subscriber);
        // the kept bytes are not charged: the reader is not charged for them either
        ReadBuffer kept = getBufferedData(last);
        if (!last) {
            dropInitialBytes();
        }
        initialBytesOverLimit = null;
        return kept;
    }

    /**
     * Drop the bytes kept past the buffer limit before the first reader subscribed, see
     * {@link #setKeepInitialBytes()}: the readers that subscribe from now on fail with the limit.
     */
    private void dropInitialBytes() {
        Exception exceeded = initialBytesOverLimit;
        if (exceeded == null) {
            return;
        }
        initialBytesOverLimit = null;
        if (buffer != null) {
            // not charged, see add0
            for (ReadBuffer rb : buffer) {
                rb.close();
            }
            buffer = null;
        }
        if (bufferSizeExceeded == null) {
            bufferSizeExceeded = exceeded;
            bufferLimitExceeded = true;
        }
    }

    /**
     * Optimized version of {@link #subscribe0} for subscribers that want to buffer the full
     * body. The returned flow will complete when the
     * input is buffered. The returned flow will always be identical to the {@code targetFlow}
     * parameter IF {@code canReturnImmediate} is false. If {@code canReturnImmediate} is true,
     * this method will SOMETIMES return an immediate ExecutionFlow instead as an optimization.
     *
     * @param targetFlow The delayed flow to use if {@code canReturnImmediate} is false and/or
     *                   we have to wait for the result
     * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
     * @param canReturnImmediate Whether we can return an immediate ExecutionFlow instead of
     *                  {@code targetFlow}, when appropriate
     * @return A flow that will complete when all data has arrived, with a buffer containing that data
     */
    protected final ExecutionFlow<ReadBuffer> subscribeFull0(DelayedExecutionFlow<ReadBuffer> targetFlow, BufferConsumer.Upstream specificUpstream, boolean canReturnImmediate) {
        assert !working;

        if (reserved <= 0) {
            throw new IllegalStateException("Need to reserve a spot first. This should not happen, StreamingNettyByteBody should guard against it");
        }

        ExecutionFlow<ReadBuffer> ret = targetFlow;
        // the target flow is completed once this buffer is done working: its completion can run
        // another operation on this buffer, e.g. a reader that closes the other reservation
        Throwable failTarget = null;
        ReadBuffer completeTarget = null;

        working = true;
        boolean last = --reserved == 0;
        subscribed = true;
        // a reader that buffers the body is held to the limit
        dropInitialBytes();
        Throwable error = this.error;
        if (error == null && bufferSizeExceeded != null) {
            error = bufferSizeExceeded;
            specificUpstream.allowDiscard();
        }
        if (error != null) {
            if (canReturnImmediate) {
                ret = ExecutionFlow.error(error);
            } else {
                failTarget = error;
            }
        } else if (complete) {
            ReadBuffer buf = getBufferedData(last);
            if (canReturnImmediate) {
                ret = ExecutionFlow.just(buf);
            } else {
                completeTarget = buf;
            }
        } else {
            if (fullSubscribers == null) {
                fullSubscribers = new ArrayList<>(1);
            }
            fullSubscribers.add(targetFlow);
        }
        afterSubscribe(last);
        working = false;

        if (failTarget != null) {
            targetFlow.completeExceptionally(failTarget);
        } else if (completeTarget != null) {
            targetFlow.complete(completeTarget);
        }
        return ret;
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
     * Release the buffered size charge of the buffered bytes, which this buffer keeps.
     */
    private void releaseBufferCharge() {
        if (buffer != null) {
            long n = 0;
            for (ReadBuffer rb : buffer) {
                n += rb.readable();
            }
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
            // only the subscribers deferred was built for: complete0 above may have run a
            // buffering subscriber's callback which subscribed another split reentrantly. That
            // subscriber has already received the buffered bytes and its completion from
            // subscribe0, since the buffer is complete by now.
            List<BufferConsumer> targets = new ArrayList<>(Objects.requireNonNull(subscribers).subList(0, deferred.size()));
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
            assert !working;

            // calculate the new total length
            long newLength = lengthSoFar + rb.readable();
            long expectedLength = this.expectedLength;
            if (expectedLength != -1 && newLength > expectedLength) {
                throw new IncorrectContentLengthException("Received more bytes than specified by Content-Length");
            }
            lengthSoFar = newLength;

            // drop messages if we're done with all subscribers
            if (complete || error != null) {
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

            working = true;
            // the copies for the subscribers there are now, delivered once the state below is
            // final: a subscriber can subscribe another split reentrantly, which then finds these
            // bytes buffered for it, and is not given them twice
            List<BufferConsumer> targets = subscribers;
            int n = targets == null ? 0 : targets.size();
            ReadBuffer single = null;
            List<ReadBuffer> copies = null;
            if (n > 0) {
                if (completeAfter) {
                    // delivered by addAndComplete
                    deferred = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        deferred.add(rb.duplicate());
                    }
                } else if (n == 1) {
                    single = rb.duplicate();
                } else {
                    copies = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        copies.add(rb.duplicate());
                    }
                }
            }
            if (initialBytesOverLimit != null) {
                // kept past the limit for a reader that streams them, see setKeepInitialBytes
                if (buffer == null) {
                    buffer = new ArrayList<>();
                }
                buffer.add(rb.move());
            } else if (reserved > 0 || fullSubscribers != null) {
                if (bufferSizeExceeded == null) {
                    bufferSizeExceeded = sizeLimitTrackers.bufferedSize().add(rb.readable());
                    if (bufferSizeExceeded != null && keepInitialBytes && !subscribed && fullSubscribers == null) {
                        // no reader yet: the upstream only sends what it sends before a reader
                        // asks for more. Keep the bytes, not charged, for a reader that streams
                        // them; any other reader fails with the limit when it subscribes
                        initialBytesOverLimit = bufferSizeExceeded;
                        bufferSizeExceeded = null;
                        releaseBufferCharge();
                    }
                    if (bufferSizeExceeded != null) {
                        bufferLimitExceeded = true;
                        discardBuffer();
                        // new subscribers will recognize that the limit has been exceeded. Streaming
                        // subscribers can proceed normally. Need to notify buffering subscribers
                        if (fullSubscribers != null) {
                            for (DelayedExecutionFlow<?> fullSubscriber : fullSubscribers) {
                                fullSubscriber.completeExceptionally(bufferSizeExceeded);
                            }
                            fullSubscribers = null;
                        }
                    }
                }
                if (bufferSizeExceeded == null) {
                    if (buffer == null) {
                        buffer = new ArrayList<>();
                    }
                    buffer.add(rb.move());
                }
            }
            working = false;
            if (single != null) {
                Objects.requireNonNull(targets).get(0).add(single);
            } else if (copies != null) {
                deliver(Objects.requireNonNull(targets), copies);
            }
        }
        return deferred;
    }

    /**
     * Deliver a copy of the added bytes to each of the given subscribers. The copies not yet
     * delivered are closed if a delivery throws.
     *
     * @param targets The subscribers
     * @param copies  The copies, one per subscriber in order
     */
    private static void deliver(List<BufferConsumer> targets, List<ReadBuffer> copies) {
        int delivered = 0;
        try {
            for (ReadBuffer copy : copies) {
                // ownership of the copy passes to the consumer with the call, even if it throws
                targets.get(delivered++).add(copy);
            }
        } finally {
            for (int i = delivered; i < copies.size(); i++) {
                copies.get(i).close();
            }
        }
    }

    /**
     * Implementation of {@link BufferConsumer#complete()}.<br>
     * Not thread safe, caller must handle concurrency.
     */
    @Override
    public void complete() {
        complete0(true);
    }

    /**
     * Complete this buffer with the given trailers, see {@link ByteBody#trailers()}.<br>
     * Not thread safe, caller must handle concurrency.
     *
     * @param trailers The trailers
     */
    public void complete(HttpHeaders trailers) {
        this.trailers.complete(trailers);
        complete0(true);
    }

    /**
     * Complete this buffer with the trailers the given stage completes with: immediately if the
     * stage is already complete, else once it completes. A stage that fails, fails this buffer.
     * <br>Not thread safe, caller must handle concurrency.
     *
     * @param trailers The trailers
     */
    public final void complete(CompletionStage<? extends HttpHeaders> trailers) {
        CompletableFuture<? extends HttpHeaders> future = trailers.toCompletableFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            complete(future.join());
            return;
        }
        future.whenComplete((headers, failure) -> submitDeferred(() -> {
            if (error != null) {
                return;
            }
            if (failure == null) {
                complete(headers == null ? NoTrailers.HEADERS : headers);
            } else {
                error(failure instanceof CompletionException ce && ce.getCause() != null ? ce.getCause() : failure);
            }
        }));
    }

    /**
     * Run the given task non-concurrently with the other operations on this buffer, like the
     * subclass runs the {@link BufferConsumer} methods.
     *
     * @param task The task
     */
    protected abstract void submitDeferred(Runnable task);

    private void complete0(boolean notifySubscribers) {
        if (expectedLength > lengthSoFar) {
            throw new IncorrectContentLengthException("Received fewer bytes than specified by Content-Length");
        }
        // no-op if the trailers are known
        trailers.complete(NoTrailers.HEADERS);
        complete = true;
        expectedLength = lengthSoFar;
        if (notifySubscribers && subscribers != null) {
            // by index, only the subscribers there are now: the completion of one can subscribe
            // another split reentrantly, which subscribe0 completes itself
            List<BufferConsumer> current = subscribers;
            for (int i = 0, n = current.size(); i < n; i++) {
                current.get(i).complete();
            }
        }
        List<DelayedExecutionFlow<ReadBuffer>> full = fullSubscribers;
        if (full != null && bufferSizeExceeded == null) {
            fullSubscribers = null;
            // the body of every subscriber is taken before the first one is completed: its
            // completion can run another operation on this buffer, e.g. a reader that closes the
            // last reservation, which discards the buffered bytes the others are still to be given
            boolean last = reserved <= 0;
            int n = full.size();
            List<ReadBuffer> bodies = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                bodies.add(getBufferedData(last && i == n - 1));
            }
            int completed = 0;
            try {
                while (completed < n) {
                    // ownership of the body passes to the subscriber with the call
                    ReadBuffer body = bodies.get(completed);
                    full.get(completed++).complete(body);
                }
            } finally {
                for (int i = completed; i < n; i++) {
                    bodies.get(i).close();
                }
            }
        }
    }

    /**
     * Implementation of {@link BufferConsumer#error(Throwable)}.<br>
     * Not thread safe, caller must handle concurrency.
     *
     * @param e The error
     */
    @Override
    public void error(Throwable e) {
        if (error != null) {
            if (error != e) {
                error.addSuppressed(e);
            }
            return;
        }

        error = e;
        failed = true;
        trailers.completeExceptionally(e);
        dropInitialBytes();
        discardBuffer();
        if (subscribers != null) {
            // by index, only the subscribers there are now: the error of one can subscribe
            // another split reentrantly, which subscribe0 fails itself
            List<BufferConsumer> current = subscribers;
            for (int i = 0, n = current.size(); i < n; i++) {
                current.get(i).error(e);
            }
        }
        if (fullSubscribers != null && bufferSizeExceeded == null) {
            for (DelayedExecutionFlow<?> fullSubscriber : fullSubscribers) {
                fullSubscriber.completeExceptionally(e);
            }
            fullSubscribers = null;
        }
    }

    /**
     * {@link BufferConsumer} that can subscribe to a {@link BaseSharedBuffer} and publish its
     * buffers to one subscriber, without Reactor. Used to implement
     * {@link ByteBody#toReadBufferPublisher()} and similar methods. The buffers that arrive before
     * they are requested are queued, and a failure is delivered after the queued buffers.
     *
     * @since 5.3.0
     */
    public static final class AsPublisher implements BufferConsumer, Publisher<ReadBuffer>, Subscription {
        private static final Subscription REJECTED = new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };

        private final BaseSharedBuffer sharedBuffer;
        /**
         * The tracker of this reader alone, see {@link #setReaderBufferLimit(long)}, or
         * {@code null} to charge the buffered size of the shared buffer.
         */
        @Nullable
        private final SizeLimitTracker ownTracker;
        /**
         * Whether this reader is not charged for the bytes it has not delivered yet, see
         * {@link #AsPublisher(BaseSharedBuffer, boolean)}.
         */
        private final boolean unbuffered;
        private boolean first = true;
        private BufferConsumer.@Nullable Upstream upstream;

        // all of the following are guarded by this
        private final ArrayDeque<ReadBuffer> queue = new ArrayDeque<>(2);
        private @Nullable Subscriber<? super ReadBuffer> subscriber;
        /**
         * The subscriber has its subscription, so signals may be delivered.
         */
        private boolean subscribed;
        private long demand;
        /**
         * The shared buffer completed or failed.
         */
        private boolean done;
        private @Nullable Throwable failure;
        private boolean cancelled;
        /**
         * The subscriber received its terminal signal.
         */
        private boolean terminated;
        /**
         * A thread delivers signals; another one that has signals to deliver leaves them to it.
         */
        private boolean draining;
        private boolean missed;

        public AsPublisher(BaseSharedBuffer sharedBuffer) {
            this(sharedBuffer, false);
        }

        /**
         * @param sharedBuffer The buffer to read
         * @param unbuffered   Whether the reader streams the bytes without holding them, e.g. to
         *                     decode them piece by piece: it is not charged for the bytes it has
         *                     not delivered yet, which the backpressure of the upstream bounds,
         *                     and it receives the bytes kept past the buffer limit before it
         *                     subscribed, see {@link #setKeepInitialBytes()}
         */
        public AsPublisher(BaseSharedBuffer sharedBuffer, boolean unbuffered) {
            this.sharedBuffer = sharedBuffer;
            this.unbuffered = unbuffered;
            long readerBufferLimit = sharedBuffer.readerBufferLimit;
            this.ownTracker = unbuffered || readerBufferLimit < 0 ? null : NotThreadSafe.create(readerBufferLimit, true).makeAtomic();
        }

        /**
         * The publisher of the buffers, once this consumer was registered with the body.
         *
         * @param upstream The upstream of this consumer
         * @return This publisher
         */
        public Publisher<ReadBuffer> publisher(BufferConsumer.Upstream upstream) {
            this.upstream = upstream;
            return this;
        }

        @Override
        public boolean isUnbuffered() {
            return unbuffered;
        }

        @Override
        public void add(ReadBuffer buf) {
            int size = buf.readable();
            if (!unbuffered) {
                Exception bufferExceededExc;
                SizeLimitTracker readerTracker = ownTracker;
                if (readerTracker != null) {
                    bufferExceededExc = readerTracker.add(size);
                } else {
                    if (first) {
                        // we need to upgrade to an atomic tracker so that we can properly subtract
                        // when a buffer is delivered, on the thread of the subscriber
                        sharedBuffer.sizeLimitTrackers = new SizeLimitTracker.TrackerPair(
                            sharedBuffer.sizeLimitTrackers.totalSize(), sharedBuffer.sizeLimitTrackers.bufferedSize().makeAtomic()
                        );
                        first = false;
                    }
                    bufferExceededExc = sharedBuffer.sizeLimitTrackers.bufferedSize().add(size);
                }
                if (bufferExceededExc != null) {
                    error(bufferExceededExc);
                    buf.close();
                    return;
                }
            }
            boolean accepted;
            synchronized (this) {
                accepted = !done && !cancelled;
                if (accepted) {
                    queue.add(buf);
                }
            }
            if (!accepted) {
                if (ownTracker != null) {
                    ownTracker.subtract(size);
                }
                buf.close();
                return;
            }
            drain();
        }

        @Override
        public void complete() {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
            }
            drain();
        }

        @Override
        public void error(Throwable e) {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                failure = e;
            }
            drain();
        }

        @Override
        public void subscribe(Subscriber<? super ReadBuffer> s) {
            boolean accepted;
            synchronized (this) {
                accepted = subscriber == null;
                if (accepted) {
                    subscriber = s;
                }
            }
            if (!accepted) {
                s.onSubscribe(REJECTED);
                s.onError(new IllegalStateException("The buffers of a body are published to a single subscriber"));
                return;
            }
            upstream().start();
            synchronized (this) {
                subscribed = true;
            }
            s.onSubscribe(this);
            // a completion or failure needs no demand
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                return;
            }
            synchronized (this) {
                demand = Long.MAX_VALUE - demand < n ? Long.MAX_VALUE : demand + n;
            }
            drain();
        }

        @Override
        public void cancel() {
            ReadBuffer[] dropped;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                dropped = queue.toArray(new ReadBuffer[0]);
                queue.clear();
            }
            for (ReadBuffer buf : dropped) {
                if (ownTracker != null) {
                    ownTracker.subtract(buf.readable());
                }
                buf.close();
            }
            upstream().allowDiscard();
            upstream().disregardBackpressure();
        }

        /**
         * Deliver the queued buffers the subscriber requested, then the completion or the failure
         * once no buffer is queued.
         */
        private void drain() {
            synchronized (this) {
                if (draining) {
                    missed = true;
                    return;
                }
                draining = true;
            }
            while (true) {
                Subscriber<? super ReadBuffer> s;
                ReadBuffer next = null;
                Throwable error = null;
                synchronized (this) {
                    s = subscriber;
                    if (s == null || !subscribed || cancelled || terminated) {
                        draining = false;
                        missed = false;
                        return;
                    }
                    if (demand > 0 && !queue.isEmpty()) {
                        next = queue.poll();
                        if (demand != Long.MAX_VALUE) {
                            demand--;
                        }
                    } else if (done && queue.isEmpty()) {
                        terminated = true;
                        error = failure;
                    } else if (missed) {
                        missed = false;
                        continue;
                    } else {
                        draining = false;
                        return;
                    }
                }
                if (next != null) {
                    int size = next.readable();
                    if (ownTracker != null) {
                        ownTracker.subtract(size);
                    } else if (!unbuffered) {
                        sharedBuffer.sizeLimitTrackers.bufferedSize().subtract(size);
                    }
                    upstream().onBytesConsumed(size);
                    s.onNext(next);
                } else if (error == null) {
                    s.onComplete();
                } else {
                    // a reader that failed, e.g. over its limit, reads nothing more
                    upstream().allowDiscard();
                    upstream().disregardBackpressure();
                    s.onError(error);
                }
            }
        }

        private BufferConsumer.Upstream upstream() {
            return Objects.requireNonNull(upstream, "The publisher was not registered with the body");
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
