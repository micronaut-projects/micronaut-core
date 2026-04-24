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
package io.micronaut.buffer.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.util.IllegalReferenceCountException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * Netty {@link ReadBuffer} implementation.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
final class NettyReadBuffer extends ReadBuffer {
    /**
     * If this is set, we copy the ByteBuf on ReadBuffer creation. This ensures it has its own
     * reference count, so incorrectly releasing the original buffer will not leave this ReadBuffer
     * in an invalid state. In practice this should not be necessary, but it can help with
     * debugging.
     */
    private static final boolean STRICT_REFCNT = Boolean.getBoolean("io.micronaut.buffer.netty.NettyReadBuffer.STRICT_REFCNT");

    @Nullable
    ByteBuf buf;

    NettyReadBuffer(ByteBuf buf) {
        checkAccessible(buf);
        if (STRICT_REFCNT) {
            ByteBuf copy = buf.copy();
            buf.release();
            this.buf = copy;
        } else {
            this.buf = buf;
        }
    }

    private static void checkAccessible(ByteBuf buf) {
        if (buf.refCnt() <= 0) {
            throw new IllegalReferenceCountException(buf.refCnt());
        }
    }

    private ByteBuf getBuf() {
        ByteBuf buf = this.buf;
        if (buf == null) {
            throw new IllegalStateException("Already released");
        }
        checkAccessible(buf);
        return buf;
    }

    @Override
    public int readable() {
        return getBuf().readableBytes();
    }

    @Override
    public ReadBuffer duplicate() {
        return new NettyReadBuffer(getBuf().retainedDuplicate());
    }

    @Override
    public ReadBuffer split(int splitPosition) {
        return new NettyReadBuffer(getBuf().readRetainedSlice(splitPosition));
    }

    @Override
    public ReadBuffer move() {
        ByteBuf b = getBuf();
        this.buf = null;
        return new NettyReadBuffer(b);
    }

    @Override
    public void toArray(byte[] destination, int offset) throws IndexOutOfBoundsException {
        ByteBuf b = getBuf();
        try {
            buf = null;
            if (offset > destination.length) {
                throw new IndexOutOfBoundsException("Offset exceeds length");
            }
            b.readBytes(destination, offset, b.readableBytes());
        } finally {
            b.release();
        }
    }

    @Override
    public String toString(Charset charset) {
        ByteBuf b = getBuf();
        try {
            buf = null;
            return b.toString(charset);
        } finally {
            b.release();
        }
    }

    @Override
    public ByteBuffer<?> toByteBuffer() {
        ByteBuf b = getBuf();
        buf = null;
        return new NettyByteBuffer(b);
    }

    @Override
    public InputStream toInputStream() {
        ByteBuf b = getBuf();
        buf = null;
        return new ByteBufInputStream(b, true);
    }

    @Override
    public <R> @Nullable R useFastHeapBuffer(Function<java.nio.ByteBuffer, R> function) {
        ByteBuf b = getBuf();
        if (b.hasArray()) {
            buf = null;
            return function.apply(java.nio.ByteBuffer.wrap(b.array(), b.arrayOffset() + b.readerIndex(), b.readableBytes()));
        } else {
            return super.useFastHeapBuffer(function);
        }
    }

    @Override
    public void transferTo(OutputStream stream) throws IOException {
        ByteBuf b = getBuf();
        buf = null;
        try {
            b.readBytes(stream, b.readableBytes());
        } finally {
            b.release();
        }
    }

    ByteBuf toByteBuf() {
        ByteBuf b = getBuf();
        buf = null;
        return b;
    }

    @Override
    public void close() {
        ByteBuf buf = this.buf;
        if (buf != null) {
            buf.release();
            this.buf = null;
        }
    }

    @Override
    protected boolean isConsumed() {
        return buf == null;
    }

    @Override
    protected byte[] peekArray(int n) {
        ByteBuf b = getBuf();
        byte[] arr = new byte[n];
        b.getBytes(b.readerIndex(), arr);
        return arr;
    }
}
