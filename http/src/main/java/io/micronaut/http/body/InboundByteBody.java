package io.micronaut.http.body;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.util.OptionalLong;

public interface InboundByteBody {
    @NonNull
    CloseableInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode);

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
        SLOWEST,
        FASTEST,
        ORIGINAL,
        NEW
    }
}
