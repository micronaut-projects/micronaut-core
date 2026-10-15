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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BodyPublishers;
import org.jetbrains.annotations.Contract;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

/**
 * Internal extensions of {@link ByteBody}.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Internal
public abstract non-sealed class InternalByteBody implements ByteBody {
    private static final String TRACK_OPERATIONS_PROPERTY = ByteBody.class.getName() + ".trackOperations";
    private static final boolean TRACK_OPERATIONS = Boolean.getBoolean(TRACK_OPERATIONS_PROPERTY);

    private @Nullable Throwable primaryOpTrace;
    private @Nullable Throwable closeTrace;
    private volatile @Nullable String claimedBy;

    /**
     * Record the first primary operation location, if tracking is enabled.
     */
    protected final void recordPrimaryOp() {
        if (TRACK_OPERATIONS && primaryOpTrace == null) {
            primaryOpTrace = new Exception("First ByteBody primary operation performed here");
        }
    }

    /**
     * Record the first close location, if tracking is enabled.
     */
    protected final void recordClosed() {
        if (TRACK_OPERATIONS && closeTrace == null) {
            closeTrace = new Exception("ByteBody closed here");
        }
    }

    /**
     * Variant of {@link #buffer()} that uses the {@link ExecutionFlow} API for extra efficiency.
     *
     * @return A flow that completes when all bytes are available
     */
    public abstract ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow();

    @Override
    public final CompletableFuture<? extends CloseableAvailableByteBody> buffer() {
        return bufferFlow().toCompletableFuture();
    }

    @Override
    public Publisher<byte[]> toByteArrayPublisher() {
        return BodyPublishers.map(toReadBufferPublisher(), ReadBuffer::toArray, BodyPublishers::closeReadBuffer);
    }

    @Override
    public abstract Publisher<ReadBuffer> toReadBufferPublisher();

    /**
     * Like {@link #toReadBufferPublisher()}, for a reader that streams the bytes without holding
     * them, e.g. to decode them piece by piece with a limit of its own: a streaming body does not
     * hold such a reader to its buffer limit, only the backpressure of its upstream bounds the
     * bytes the reader has not received yet.
     *
     * @return The publisher
     * @since 5.3.0
     */
    public Publisher<ReadBuffer> toUnbufferedReadBufferPublisher() {
        return toReadBufferPublisher();
    }

    /**
     * Throw the standard "already claimed" error and attach stored traces when tracking is enabled.
     */
    @Contract("-> fail")
    protected final void failClaim() {
        String reader = claimedBy;
        IllegalStateException e = new IllegalStateException(reader != null ? reader :
            "Request body has already been claimed: Two conflicting sites are trying to access the request body. " +
                "If this is intentional, the first user must ByteBody#split the body. " +
                "To find out where the body was claimed, enable the -D" + TRACK_OPERATIONS_PROPERTY + "=true system property."
        );
        if (TRACK_OPERATIONS) {
            if (primaryOpTrace != null) {
                e.addSuppressed(primaryOpTrace);
            }
            if (closeTrace != null) {
                e.addSuppressed(closeTrace);
            }
        }
        throw e;
    }

    /**
     * Describe the read that claimed the given body: a later access to the body fails with this
     * message instead of the generic one, e.g. for a route that reads a body a filter consumed.
     * The first description is kept.
     *
     * @param body    The body
     * @param message The message of the failure of a later access, which names the read
     * @since 5.3.0
     */
    public static void describeClaim(ByteBody body, String message) {
        if (body instanceof InternalByteBody internal && internal.claimedBy == null) {
            internal.claimedBy = message;
        }
    }

    /**
     * The description of the read that claimed the given body, see {@link #describeClaim}.
     *
     * @param body The body
     * @return The message of the failure of a later access, or {@code null} if no read described
     * itself
     * @since 5.3.0
     */
    public static @Nullable String claimDescription(ByteBody body) {
        return body instanceof InternalByteBody internal ? internal.claimedBy : null;
    }

    /**
     * Read the bytes of the body without holding them, see
     * {@link #toUnbufferedReadBufferPublisher()}.
     *
     * @param body The body
     * @return The publisher
     * @since 5.3.0
     */
    public static Publisher<ReadBuffer> toUnbufferedReadBufferPublisher(ByteBody body) {
        if (body instanceof InternalByteBody internal) {
            return internal.toUnbufferedReadBufferPublisher();
        } else {
            return body.toReadBufferPublisher();
        }
    }

    public static ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow(ByteBody body) {
        if (body instanceof InternalByteBody internal) {
            return internal.bufferFlow();
        } else {
            return CompletableFutureExecutionFlow.just(body.buffer());
        }
    }
}
