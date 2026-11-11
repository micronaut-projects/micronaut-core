/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.json.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.body.stream.PieceReaders;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A {@link ReadBuffer} of the content of a {@link ByteBuffer} of the input of a chunked reader,
 * without copying it: the views split off it share the buffer, which is released once, when the
 * last view is closed.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class SharedReadBuffer extends ReadBuffer {
    private final Owner owner;
    private final java.nio.ByteBuffer view;
    private boolean closed;

    private SharedReadBuffer(Owner owner, java.nio.ByteBuffer view) {
        this.owner = owner;
        this.view = view;
    }

    /**
     * The read buffer of a buffer of the input of a chunked reader, which takes it over: the
     * content is shared, not copied, unless the buffer has no NIO view of it.
     *
     * @param buffer The buffer
     * @return The read buffer
     */
    static ReadBuffer adapt(ByteBuffer<?> buffer) {
        java.nio.ByteBuffer view;
        try {
            view = buffer.asNioBuffer();
        } catch (UnsupportedOperationException e) {
            return PieceReaders.adapt(buffer);
        }
        if (buffer instanceof ReferenceCounted counted) {
            return new SharedReadBuffer(new Owner(counted), view);
        }
        return ReadBufferFactory.getJdkFactory().adapt(view);
    }

    private java.nio.ByteBuffer view() {
        if (closed) {
            throw new IllegalStateException("Buffer already closed or consumed");
        }
        return view;
    }

    @Override
    public int readable() {
        return view().remaining();
    }

    @Override
    public ReadBuffer duplicate() {
        java.nio.ByteBuffer v = view();
        owner.incrementAndGet();
        return new SharedReadBuffer(owner, v.duplicate());
    }

    @Override
    public ReadBuffer split(int splitPosition) {
        java.nio.ByteBuffer v = view();
        if (splitPosition > v.remaining()) {
            throw new IndexOutOfBoundsException();
        }
        java.nio.ByteBuffer slice = v.slice(v.position(), splitPosition);
        v.position(v.position() + splitPosition);
        owner.incrementAndGet();
        return new SharedReadBuffer(owner, slice);
    }

    @Override
    public ReadBuffer move() {
        java.nio.ByteBuffer v = view();
        closed = true;
        return new SharedReadBuffer(owner, v);
    }

    @Override
    public void toArray(byte[] destination, int offset) {
        java.nio.ByteBuffer v = view();
        try {
            if (offset > destination.length || destination.length - offset < v.remaining()) {
                throw new IndexOutOfBoundsException();
            }
            v.get(v.position(), destination, offset, v.remaining());
        } finally {
            close();
        }
    }

    @Override
    public <R> @Nullable R useFastHeapBuffer(Function<java.nio.ByteBuffer, R> function) {
        java.nio.ByteBuffer v = view();
        if (!v.hasArray()) {
            return null;
        }
        try {
            return function.apply(v);
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            owner.release();
        }
    }

    @Override
    protected boolean isConsumed() {
        return closed;
    }

    @Override
    protected byte[] peekArray(int n) {
        java.nio.ByteBuffer v = view();
        byte[] bytes = new byte[n];
        v.get(v.position(), bytes, 0, n);
        return bytes;
    }

    /**
     * The buffer the views share, and the number of views that are open.
     */
    @SuppressWarnings("java:S2160") // identity only
    private static final class Owner extends AtomicInteger {
        private final transient ReferenceCounted buffer;

        Owner(ReferenceCounted buffer) {
            super(1);
            this.buffer = buffer;
        }

        void release() {
            if (decrementAndGet() == 0) {
                buffer.release();
            }
        }
    }
}
