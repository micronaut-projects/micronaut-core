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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Netty {@link ReadBuffer} implementation.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
final class NettyReadBuffer extends ReadBuffer {
    ByteBuf buf;

    NettyReadBuffer(ByteBuf buf) {
        this.buf = buf;
    }

    private ByteBuf getBuf() {
        ByteBuf buf = this.buf;
        if (buf == null) {
            throw new IllegalStateException("Already released");
        }
        return buf;
    }

    @Override
    public int readable() {
        return getBuf().readableBytes();
    }

    @Override
    public @NonNull ReadBuffer duplicate() {
        return new NettyReadBuffer(getBuf().retainedDuplicate());
    }

    @Override
    public @NonNull ReadBuffer split(int splitPosition) {
        return new NettyReadBuffer(getBuf().readRetainedSlice(splitPosition));
    }

    @Override
    public ReadBuffer move() {
        ByteBuf b = getBuf();
        this.buf = null;
        return new NettyReadBuffer(b);
    }

    @Override
    public void toArray(byte @NonNull [] destination, int offset) throws IndexOutOfBoundsException {
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
    public @NonNull String toString(Charset charset) {
        ByteBuf b = getBuf();
        try {
            buf = null;
            return b.toString(charset);
        } finally {
            b.release();
        }
    }

    @Override
    public @NonNull ByteBuffer<?> toByteBuffer() {
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
    public void transferTo(@NonNull OutputStream stream) throws IOException {
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
