package io.micronaut.http.body;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;

import java.util.OptionalLong;

public interface ImmediateInboundByteBody extends InboundByteBody {
    @NonNull
    CloseableImmediateInboundByteBody split();

    @Override
    default @NonNull CloseableImmediateInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        return split();
    }

    long length();

    @Override
    @NonNull
    default OptionalLong expectedLength() {
        return OptionalLong.of(length());
    }

    byte @NonNull [] toByteArray();

    @NonNull
    ByteBuffer<?> toByteBuffer();
}
