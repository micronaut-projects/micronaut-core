package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.OptionalLong;

/**
 * This class represents a stream of bytes from an HTTP connection. These bytes may be streamed or
 * fully in memory, depending on implementation.
 * <p>Each {@link InboundByteBody} may only be used once for a "primary" operation (such as
 * {@link #toInputStream()}). However, <i>before</i> that primary operation, it may be
 * {@link #split() split} multiple times. Splitting returns a new {@link InboundByteBody} that is
 * independent. That means if you want to do two primary operations on the same
 * {@link InboundByteBody}, you can instead split it once and then do one of the primary operations
 * on the body returned by {@link #split()}.
 * <p>To ensure resource cleanup, {@link #split()} returns a {@link CloseableInboundByteBody}. This
 * body must be closed if no terminal operation is performed, otherwise there may be a memory leak
 * or stalled connection!
 * <p>An individual {@link InboundByteBody} is <i>not</i> thread-safe: You may not call
 * {@link #split()} concurrently from multiple threads for example. However, the new
 * {@link InboundByteBody} returned from {@link #split()} is independent, so you may use it on a
 * different thread as this one.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface InboundByteBody {
    /**
     * Equivalent to {@code split(SplitBackpressureMode.SLOWEST)}.
     *
     * @return The newly split body. Must be closed by the caller, unless a terminal operation is
     * performed on it
     */
    @NonNull
    default CloseableInboundByteBody split() {
        return split(SplitBackpressureMode.SLOWEST);
    }

    /**
     * Create a new, independent {@link InboundByteBody} that contains the same data as this one.
     *
     * @param backpressureMode How to handle backpressure between the old and new body. See
     *                         {@link SplitBackpressureMode} documentation
     * @return The newly split body. Must be closed by the caller, unless a terminal operation is
     * performed on it
     */
    @NonNull
    CloseableInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode);

    /**
     *
     *
     * @return This body
     */
    @NonNull
    default InboundByteBody allowDiscard() {
        return this;
    };

    /**
     * Get the expected length of this body, if known (either from {@code Content-Length} or from
     * previous buffering). The actual length will never exceed this value, though it may sometimes
     * be lower if there is a connection error.
     * <p>This value may go from {@link OptionalLong#empty()} to a known value over the lifetime of
     * this body.
     * <p>This is <i>not</i> a primary operation and does not modify this {@link InboundByteBody}.
     *
     * @return The expected length of this body
     */
    @NonNull
    OptionalLong expectedLength();

    /**
     * Get this body as an {@link InputStream}.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return The streamed bytes
     */
    @NonNull
    InputStream toInputStream();

    /**
     * Get this body as a reactive stream of byte arrays.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return The streamed bytes
     */
    @NonNull
    Publisher<byte[]> toByteArrayPublisher();

    /**
     * Get this body as a reactive stream of {@link ByteBuffer}s. Note that the buffers may be
     * {@link io.micronaut.core.io.buffer.ReferenceCounted reference counted}, and the caller must
     * take care of releasing them.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return The streamed bytes
     */
    @NonNull
    Publisher<ByteBuffer<?>> toByteBufferPublisher();

    /**
     * Buffer the full body and return an {@link ExecutionFlow} that will complete when all bytes
     * are available, or an error occurs.
     *
     * @return A flow that completes when all bytes are available
     */
    @NonNull
    ExecutionFlow<? extends CloseableImmediateInboundByteBody> buffer();

    /**
     * This enum controls how backpressure should be handled if one of the two bodies
     * ("downstreams") is consuming data slower than the other.
     */
    enum SplitBackpressureMode {
        /**
         * Request data from upstream at the pace of the slowest downstream.
         */
        SLOWEST,
        /**
         * Request data from upstream at the pace of the fastest downstream. Note that this can
         * cause the slower downstream to buffer or drop messages, if it can't keep up.
         */
        FASTEST,
        /**
         * Request data from upstream at the pace of the original downstream. The pace of the
         * consumption of the new body returned by {@link #split(SplitBackpressureMode)} is ignored.
         * Note that this can cause the new downstream to buffer or drop messages, if it can't keep
         * up.
         */
        ORIGINAL,
        /**
         * Request data from upstream at the pace of the new downstream. The pace of the
         * consumption of the original body is ignored. Note that this can cause the new downstream
         * to buffer or drop messages, if it can't keep up.
         */
        NEW
    }
}
