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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * {@link io.micronaut.http.body.AvailableByteBody} implementation based on a simple byte array.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Experimental
public final class AvailableByteArrayBody implements CloseableAvailableByteBody, InternalByteBody {
    // originally from micronaut-servlet

    private final ByteBufferFactory<?, ?> bufferFactory;
    private byte[] array;

    private AvailableByteArrayBody(ByteBufferFactory<?, ?> bufferFactory, byte[] array) {
        this.bufferFactory = bufferFactory;
        this.array = array;
    }

    @NonNull
    public static AvailableByteArrayBody create(@NonNull ByteBufferFactory<?, ?> bufferFactory, byte @NonNull [] array) {
        ArgumentUtils.requireNonNull("bufferFactory", bufferFactory);
        ArgumentUtils.requireNonNull("array", array);
        return new AvailableByteArrayBody(bufferFactory, array);
    }

    @Override
    public @NonNull CloseableAvailableByteBody split() {
        if (array == null) {
            BaseSharedBuffer.failClaim();
        }
        return new AvailableByteArrayBody(bufferFactory, array);
    }

    @Override
    public @NonNull InputStream toInputStream() {
        return new ByteArrayInputStream(array);
    }

    @Override
    public long length() {
        if (array == null) {
            BaseSharedBuffer.failClaim();
        }
        return array.length;
    }

    @Override
    public byte @NonNull [] toByteArray() {
        byte[] a = array;
        if (a == null) {
            BaseSharedBuffer.failClaim();
        }
        array = null;
        BaseSharedBuffer.logClaim();
        return a;
    }

    @Override
    public @NonNull ByteBuffer<?> toByteBuffer() {
        return bufferFactory.wrap(toByteArray());
    }

    @Override
    public @NonNull CloseableByteBody move() {
        return new AvailableByteArrayBody(bufferFactory, toByteArray());
    }

    @Override
    public void close() {
        array = null;
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        return ExecutionFlow.just(new AvailableByteArrayBody(bufferFactory, toByteArray()));
    }
}
