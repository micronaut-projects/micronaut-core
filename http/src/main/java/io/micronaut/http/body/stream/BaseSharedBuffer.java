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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base type for a shared buffer that distributes a single {@link BufferConsumer} input to multiple
 * streaming {@link io.micronaut.http.body.ByteBody}s.<br>
 * The subclass handles concurrency (for netty, event loop) and the specific buffer type
 * (for netty, ByteBuf).
 *
 * @param <C> The {@link BufferConsumer} type for the specific buffer type
 * @param <F> The type returned to {@link #subscribeFull0} subscribers. This is usually just the
 *           buffer type
 */
@Internal
public abstract class BaseSharedBuffer<C extends BufferConsumer, F> {
    private static final Class<ByteBody> SPLIT_LOG_CLASS = ByteBody.class;
    private static final Logger SPLIT_LOG = LoggerFactory.getLogger(SPLIT_LOG_CLASS);

    private final BodySizeLimits limits;
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
    private Throwable error;
    /**
     * Number of reserved subscriber spots. A new subscription MUST be preceded by a
     * reservation, and every reservation MUST have a subscription.
     */
    private int reserved = 1;
    /**
     * Active subscribers.
     */
    private List<@NonNull C> subscribers;
    /**
     * Active subscribers that need the fully buffered body.
     */
    private List<@NonNull DelayedExecutionFlow<F>> fullSubscribers;
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

    public BaseSharedBuffer(BodySizeLimits limits, BufferConsumer.Upstream rootUpstream) {
        this.limits = limits;
        this.rootUpstream = rootUpstream;
    }

    @Contract("-> fail")
    public static void failClaim() {
        throw new IllegalStateException("Request body has already been claimed: Two conflicting sites are trying to access the request body. If this is intentional, the first user must ByteBody#split the body. To find out where the body was claimed, turn on TRACE logging for " + SPLIT_LOG_CLASS.getName() + ".");
    }

    public static void logClaim() {
        if (SPLIT_LOG.isTraceEnabled()) {
            SPLIT_LOG.trace("Body split at this location. This is not an error, but may aid in debugging other errors", new Exception());
        }
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

    public final BodySizeLimits getLimits() {
        return limits;
    }

    public final BufferConsumer.Upstream getRootUpstream() {
        return rootUpstream;
    }

    public final void setExpectedLengthFrom(String contentLength) {
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
        if (parsed > limits.maxBodySize()) {
            error(new ContentLengthExceededException(limits.maxBodySize(), parsed));
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
    protected abstract void forwardInitialBuffer(@Nullable C subscriber, boolean last);

    /**
     * Called after a subscribe operation. Used for leak detection.
     *
     * @param last {@code true} iff this was the last reservation
     */
    protected void afterSubscribe(boolean last) {
    }

    /**
     * Called after {@link BufferConsumer#complete() completion} to get the data that should be
     * forwarded to a {@link #subscribeFull0} subscriber.
     *
     * @param last {@code true} iff this was the last reservation and the buffer can be discarded
     *                         after this call
     * @return The full result that should be returned from the {@link #subscribeFull0} execution
     * flow
     */
    protected abstract F subscribeFullResult(boolean last);

    /**
     * Add a subscriber. Must be preceded by a reservation.<br>
     * Not thread safe, caller must handle concurrency.
     *
     * @param subscriber       The subscriber to add. Can be {@code null}, then the bytes will just be discarded
     * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
     */
    protected final void subscribe0(@Nullable C subscriber, BufferConsumer.Upstream specificUpstream) {
        assert !working;

        if (reserved == 0) {
            throw new IllegalStateException("Need to reserve a spot first");
        }

        working = true;
        boolean last = --reserved == 0;
        if (subscriber != null) {
            if (subscribers == null) {
                subscribers = new ArrayList<>(1);
            }
            subscribers.add(subscriber);
            forwardInitialBuffer(subscriber, last);
            if (error != null) {
                subscriber.error(error);
            } else if (lengthSoFar > limits.maxBufferSize()) {
                subscriber.error(new BufferLengthExceededException(limits.maxBufferSize(), lengthSoFar));
                specificUpstream.allowDiscard();
            }
            if (complete) {
                subscriber.complete();
            }
        } else {
            forwardInitialBuffer(null, last);
        }
        afterSubscribe(last);
        working = false;
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
    protected final ExecutionFlow<F> subscribeFull0(DelayedExecutionFlow<F> targetFlow, BufferConsumer.Upstream specificUpstream, boolean canReturnImmediate) {
        assert !working;

        if (reserved <= 0) {
            throw new IllegalStateException("Need to reserve a spot first. This should not happen, StreamingNettyByteBody should guard against it");
        }

        ExecutionFlow<F> ret = targetFlow;

        working = true;
        boolean last = --reserved == 0;
        Throwable error = this.error;
        if (error == null && lengthSoFar > limits.maxBufferSize()) {
            error = new BufferLengthExceededException(limits.maxBufferSize(), lengthSoFar);
            specificUpstream.allowDiscard();
        }
        if (error != null) {
            if (canReturnImmediate) {
                ret = ExecutionFlow.error(error);
            } else {
                targetFlow.completeExceptionally(error);
            }
        } else if (complete) {
            F buf = subscribeFullResult(last);
            if (canReturnImmediate) {
                ret = ExecutionFlow.just(buf);
            } else {
                targetFlow.complete(buf);
            }
        } else {
            if (fullSubscribers == null) {
                fullSubscribers = new ArrayList<>(1);
            }
            fullSubscribers.add(targetFlow);
        }
        afterSubscribe(last);
        working = false;

        return ret;
    }

    /**
     * Forward the input buffer to the given list of consumers.
     *
     * @param consumers The consumers to forward the data to
     * @see #add(int)
     */
    protected abstract void addForward(List<C> consumers);

    /**
     * Do not buffer the input buffer.
     *
     * @see #add(int)
     */
    protected void addDoNotBuffer() {
    }

    /**
     * Buffer the input buffer.
     *
     * @see #add(int)
     */
    protected abstract void addBuffer();

    /**
     * Discard the previously buffered bytes.
     *
     * @see #add(int)
     */
    protected abstract void discardBuffer();

    /**
     * This method implements the {@link BufferConsumer} {@code add} logic in a
     * buffer-type-independent way. It is not thread-safe: The subclass must take care of
     * concurrency. The caller should store the actual buffer to be added in a field, then call
     * this method, and use the stored buffer in the calls this method makes to
     * {@link #addBuffer()}, {@link #addDoNotBuffer()} and {@link #addBuffer()}. After this method
     * completes, the field can be cleared and those methods will not be called again. Example:
     *
     * <pre>
     * {@code
     *   ByteBuf adding;
     *
     *   public void add(ByteBuf buf) {
     *       this.adding = buf;
     *       add(buf.readableBytes());
     *       this.adding = null;
     *   }
     *
     *   @Override
     *   protected void addForward(List<ByteBufConsumer> consumers) {
     *      for (ByteBufConsumer c : consumers) {
     *          c.add(this.adding);
     *      }
     *   }
     * }
     * </pre>
     *
     * @param n The number of bytes to add
     */
    protected final void add(int n) {
        assert !working;

        // calculate the new total length
        long newLength = lengthSoFar + n;
        long expectedLength = this.expectedLength;
        if (expectedLength != -1 && newLength > expectedLength) {
            throw new IllegalStateException("Received more bytes than specified by Content-Length");
        }
        lengthSoFar = newLength;

        // drop messages if we're done with all subscribers
        if (complete || error != null) {
            addDoNotBuffer();
            return;
        }
        if (newLength > limits.maxBodySize()) {
            // for maxBodySize, all subscribers get the error
            addDoNotBuffer();
            error(new ContentLengthExceededException(limits.maxBodySize(), newLength));
            rootUpstream.allowDiscard();
            return;
        }

        working = true;
        if (subscribers != null) {
            addForward(subscribers);
        }
        if (reserved > 0 || fullSubscribers != null) {
            if (newLength > limits.maxBufferSize()) {
                // new subscribers will recognize that the limit has been exceeded. Streaming
                // subscribers can proceed normally. Need to notify buffering subscribers
                addDoNotBuffer();
                discardBuffer();
                if (fullSubscribers != null) {
                    Exception e = new BufferLengthExceededException(limits.maxBufferSize(), lengthSoFar);
                    for (DelayedExecutionFlow<?> fullSubscriber : fullSubscribers) {
                        fullSubscriber.completeExceptionally(e);
                    }
                }
            } else {
                addBuffer();
            }
        } else {
            addDoNotBuffer();
        }
        working = false;
    }

    /**
     * Implementation of {@link BufferConsumer#complete()}.<br>
     * Not thread safe, caller must handle concurrency.
     */
    public void complete() {
        if (expectedLength > lengthSoFar) {
            throw new IllegalStateException("Received fewer bytes than specified by Content-Length");
        }
        complete = true;
        expectedLength = lengthSoFar;
        if (subscribers != null) {
            for (BufferConsumer subscriber : subscribers) {
                subscriber.complete();
            }
        }
        if (fullSubscribers != null) {
            boolean last = reserved <= 0;
            for (Iterator<DelayedExecutionFlow<F>> iterator = fullSubscribers.iterator(); iterator.hasNext(); ) {
                DelayedExecutionFlow<F> fullSubscriber = iterator.next();
                fullSubscriber.complete(subscribeFullResult(last && !iterator.hasNext()));
            }
        }
    }

    /**
     * Implementation of {@link BufferConsumer#error(Throwable)}.<br>
     * Not thread safe, caller must handle concurrency.
     *
     * @param e The error
     */
    public void error(Throwable e) {
        if (error != null) {
            error.addSuppressed(e);
            return;
        }

        error = e;
        discardBuffer();
        if (subscribers != null) {
            for (BufferConsumer subscriber : subscribers) {
                subscriber.error(e);
            }
        }
        if (fullSubscribers != null) {
            for (DelayedExecutionFlow<?> fullSubscriber : fullSubscribers) {
                fullSubscriber.completeExceptionally(e);
            }
        }
    }

    /**
     * {@link BufferConsumer} that can subscribe to a {@link BaseSharedBuffer} and return the
     * buffer as a {@link Flux}. Used to implement {@link ByteBody#toByteBufferPublisher()} and
     * similar methods.<br>
     * Subclass implements the specific {@link BufferConsumer} {@code add} method and
     * {@link #size(Object)}.
     *
     * @param <B> The buffer type
     */
    public abstract static class AsFlux<B> implements BufferConsumer {
        private final BaseSharedBuffer<?, ?> sharedBuffer;
        private final AtomicLong unconsumed = new AtomicLong(0);
        private final Sinks.Many<B> sink = Sinks.many().unicast().onBackpressureBuffer();

        public AsFlux(BaseSharedBuffer<?, ?> sharedBuffer) {
            this.sharedBuffer = sharedBuffer;
        }

        protected abstract int size(B buf);

        public final boolean add0(B buf) {
            long newLength = unconsumed.addAndGet(size(buf));
            if (newLength > sharedBuffer.getLimits().maxBufferSize()) {
                sink.tryEmitError(new BufferLengthExceededException(sharedBuffer.getLimits().maxBufferSize(), newLength));
                return false;
            } else {
                return sink.tryEmitNext(buf) == Sinks.EmitResult.OK;
            }
        }

        @Override
        public final void complete() {
            sink.tryEmitComplete();
        }

        @Override
        public final void error(Throwable e) {
            sink.tryEmitError(e);
        }

        public final Flux<B> asFlux(Upstream upstream) {
            return sink.asFlux()
                .doOnSubscribe(s -> upstream.start())
                .doOnNext(bb -> {
                    int size = size(bb);
                    unconsumed.addAndGet(-size);
                    upstream.onBytesConsumed(size);
                })
                .doOnCancel(() -> {
                    upstream.allowDiscard();
                    upstream.disregardBackpressure();
                });
        }
    }
}
