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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.InternalByteBody;

import java.util.Objects;

/**
 * {@link io.micronaut.http.body.AvailableByteBody} implementation based on a simple byte array.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Experimental
public final class AvailableByteArrayBody extends InternalByteBody implements CloseableAvailableByteBody {
    private ReadBuffer readBuffer;

    private AvailableByteArrayBody(ReadBuffer readBuffer) {
        this.readBuffer = Objects.requireNonNull(readBuffer, "readBuffer");
    }

    /**
     * Creates a new {@link AvailableByteArrayBody} instance.
     *
     * @param bufferFactory the {@link ByteBufferFactory} to use for creating buffers
     * @param array         the byte array to wrap
     * @return a new {@link AvailableByteArrayBody} instance
     * @deprecated Construct through {@link io.micronaut.http.body.ByteBodyFactory} instead
     */
    @Deprecated
    @NonNull
    public static AvailableByteArrayBody create(@NonNull ByteBufferFactory<?, ?> bufferFactory, byte @NonNull [] array) {
        ArgumentUtils.requireNonNull("bufferFactory", bufferFactory);
        ArgumentUtils.requireNonNull("array", array);
        return new AvailableByteArrayBody(ReadBufferFactory.getJdkFactory().adapt(array));
    }

    @NonNull
    public static AvailableByteArrayBody create(@NonNull ReadBuffer readBuffer) {
        return new AvailableByteArrayBody(readBuffer);
    }

    @Override
    public @NonNull CloseableAvailableByteBody split() {
        if (readBuffer == null) {
            BaseSharedBuffer.failClaim();
        }
        return new AvailableByteArrayBody(readBuffer.duplicate());
    }

    @Override
    public long length() {
        if (readBuffer == null) {
            BaseSharedBuffer.failClaim();
        }
        return readBuffer.readable();
    }

    @Override
    public byte @NonNull [] toByteArray() {
        try (ReadBuffer rb = toReadBuffer()) {
            return rb.toArray();
        }
    }

    @Override
    public @NonNull ReadBuffer toReadBuffer() {
        ReadBuffer a = readBuffer;
        if (a == null) {
            BaseSharedBuffer.failClaim();
        }
        readBuffer = null;
        BaseSharedBuffer.logClaim();
        return a;
    }

    @Override
    public @NonNull CloseableAvailableByteBody move() {
        return new AvailableByteArrayBody(toReadBuffer());
    }

    @Override
    public void close() {
        ReadBuffer rb = readBuffer;
        if (rb != null) {
            rb.close();
            readBuffer = null;
        }
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        return ExecutionFlow.just(move());
    }

    @Internal
    public ReadBuffer peek() {
        ReadBuffer b = readBuffer;
        if (b == null) {
            BaseSharedBuffer.failClaim();
        }
        return b;
    }
}
