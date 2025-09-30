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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import org.jetbrains.annotations.Contract;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 * This class represents a stream of bytes from an HTTP connection. These bytes may be streamed or
 * fully in memory, depending on implementation.
 * <p>Each {@link ByteBody} may only be used once for a "primary" operation (such as
 * {@link #toInputStream()}). However, <i>before</i> that primary operation, it may be
 * {@link #split() split} multiple times. Splitting returns a new {@link ByteBody} that is
 * independent. That means if you want to do two primary operations on the same
 * {@link ByteBody}, you can instead split it once and then do one of the primary operations
 * on the body returned by {@link #split()}.
 * <p>To ensure resource cleanup, {@link #split()} returns a {@link CloseableByteBody}. This
 * body must be closed if no terminal operation is performed, otherwise there may be a memory leak
 * or stalled connection!
 * <p>An individual {@link ByteBody} is <i>not</i> thread-safe: You may not call
 * {@link #split()} concurrently from multiple threads for example. However, the new
 * {@link ByteBody} returned from {@link #split()} is independent, so you may use it on a
 * different thread as this one.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface ByteBody {
    /**
     * Equivalent to {@code split(SplitBackpressureMode.SLOWEST)}.
     *
     * @return The newly split body. Must be closed by the caller, unless a terminal operation is
     * performed on it
     */
    @NonNull
    default CloseableByteBody split() {
        return split(SplitBackpressureMode.SLOWEST);
    }

    /**
     * Create a new, independent {@link ByteBody} that contains the same data as this one.
     *
     * @param backpressureMode How to handle backpressure between the old and new body. See
     *                         {@link SplitBackpressureMode} documentation
     * @return The newly split body. Must be closed by the caller, unless a terminal operation is
     * performed on it
     */
    @NonNull
    CloseableByteBody split(@NonNull SplitBackpressureMode backpressureMode);

    /**
     * Signal that the upstream may discard any remaining body data. Only if all consumers of the
     * body allow discarding will the body be discarded, otherwise it will still be sent to all
     * consumers. It is an optional operation.
     * <p>Discarding may be implemented e.g. by closing the input side of an HTTP/2 stream.
     * <p>This method must be called before any primary operation.
     *
     * @return This body
     */
    @Contract("-> this")
    @NonNull
    default ByteBody allowDiscard() {
        return this;
    }

    /**
     * Get the expected length of this body, if known (either from {@code Content-Length} or from
     * previous buffering). The actual length will never exceed this value, though it may sometimes
     * be lower if there is a connection error.
     * <p>This value may go from {@link OptionalLong#empty()} to a known value over the lifetime of
     * this body.
     * <p>This is <i>not</i> a primary operation and does not modify this {@link ByteBody}.
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
     * Buffer the full body and return an {@link CompletableFuture} that will complete when all
     * bytes are available, or an error occurs.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     *
     * @return A future that completes when all bytes are available
     */
    @NonNull
    CompletableFuture<? extends CloseableAvailableByteBody> buffer();

    /**
     * Create a new {@link CloseableByteBody} with the same content but an independent lifecycle,
     * claiming this body in the process.
     * <p>This is a primary operation. After this operation, no other primary operation or
     * {@link #split()} may be done.
     * <p>The purpose of this method is to <i>move</i> the data to a different component in an
     * application, making clear that the receiving component claims ownership of the body. If the
     * sending component then closes the original {@link ByteBody} for example, it will have no
     * impact on the new {@link CloseableByteBody} that the receiver is working with.
     *
     * @return A new {@link CloseableByteBody} with the same content.
     * @since 4.8.0
     */
    @NonNull
    CloseableByteBody move();

    /**
     * Debug trace for leak detection.
     */
    default void touch() {
    }

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

    /**
     * Exception that is sent to subscribers when the body is discarded as a result of
     * {@link #allowDiscard()} calls.
     */
    final class BodyDiscardedException extends IOException {
        static final BodyDiscardedException INSTANCE = new BodyDiscardedException();

        BodyDiscardedException() {
        }

        /**
         * Get an instance of this exception. At the moment this is a singleton without stack trace,
         * but this may change in the future.
         *
         * @return The instance
         */
        public static BodyDiscardedException create() {
            return INSTANCE;
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
