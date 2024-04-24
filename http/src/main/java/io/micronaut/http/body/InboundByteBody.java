package io.micronaut.http.body;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.OptionalLong;

public interface InboundByteBody {
    @NonNull
    default CloseableInboundByteBody split() {
        return split(SplitBackpressureMode.SLOWEST);
    }

    @NonNull
    CloseableInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode);

    /**
     * Get the expected length of this body, if known (either from {@code Content-Length} or from
     * previous buffering). The actual length will never exceed this value, though it may sometimes
     * be lower if there is a connection error.
     *
     * @return The expected length of this body
     */
    @NonNull
    OptionalLong expectedLength();

    @NonNull
    InputStream toInputStream();

    @NonNull
    Publisher<byte[]> toByteArrayPublisher();

    @NonNull
    Publisher<ByteBuffer<?>> toByteBufferPublisher();

    @NonNull
    ExecutionFlow<? extends CloseableImmediateInboundByteBody> buffer();

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
