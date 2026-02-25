/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.io.buffer;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * {@link ReadBuffer} implementation based on NIO {@link ByteBuffer}.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
final class NioReadBuffer extends ReadBuffer {
    private static final LeakTracker.Factory<NioReadBuffer> TRACKER_FACTORY = LeakTracker.Factory.forClass(NioReadBuffer.class);

    @Nullable
    private final LeakTracker<NioReadBuffer> tracker = TRACKER_FACTORY.track(this);

    private final ByteBuffer buffer;
    private boolean closed;

    NioReadBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Buffer already closed or consumed");
        }
    }

    @Override
    public int readable() {
        checkOpen();
        return buffer.remaining();
    }

    @Override
    public ReadBuffer duplicate() {
        checkOpen();
        return new NioReadBuffer(buffer.duplicate());
    }

    @Override
    public ReadBuffer split(int splitPosition) {
        checkOpen();
        if (splitPosition > buffer.remaining()) {
            throw new IndexOutOfBoundsException();
        }
        ByteBuffer slice = buffer.slice(buffer.position(), splitPosition);
        buffer.position(buffer.position() + splitPosition);
        return new NioReadBuffer(slice);
    }

    @Override
    public ReadBuffer move() {
        checkOpen();
        close();
        return new NioReadBuffer(buffer);
    }

    @Override
    public void toArray(byte[] destination, int offset) {
        checkOpen();
        close();
        if (offset > destination.length || destination.length - offset < buffer.remaining()) {
            throw new IndexOutOfBoundsException();
        }
        buffer.get(destination, offset, buffer.remaining());
    }

    @Override
    public byte[] toArray() {
        checkOpen();
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.position() == 0 && buffer.remaining() == buffer.array().length) {
            close();
            return buffer.array();
        } else {
            return super.toArray();
        }
    }

    @Override
    public InputStream toInputStream() {
        checkOpen();
        if (buffer.hasArray()) {
            close();
            return new ByteArrayInputStream(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            return super.toInputStream();
        }
    }

    @Override
    public <R> @Nullable R useFastHeapBuffer(Function<ByteBuffer, R> function) {
        checkOpen();
        if (buffer.hasArray()) {
            close();
            return function.apply(buffer);
        } else {
            return super.useFastHeapBuffer(function);
        }
    }

    @Override
    public void close() {
        closed = true;
        if (tracker != null) {
            tracker.close(this);
        }
    }

    @Override
    protected boolean isConsumed() {
        return closed;
    }

    @Override
    protected byte[] peekArray(int n) {
        byte[] bytes = new byte[n];
        buffer.get(buffer.position(), bytes, 0, n);
        return bytes;
    }
}
