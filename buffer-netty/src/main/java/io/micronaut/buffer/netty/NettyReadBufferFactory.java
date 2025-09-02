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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.util.functional.ThrowingConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Netty-based {@link ReadBufferFactory}. Also has additional utilities for dealing with netty
 * buffers.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public final class NettyReadBufferFactory extends ReadBufferFactory {
    private final ByteBufAllocator allocator;

    private NettyReadBufferFactory(ByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    /**
     * Get a buffer factory associated with the given allocator.
     *
     * @param allocator The allocator to use
     * @return The buffer factory
     */
    @NonNull
    public static NettyReadBufferFactory of(@NonNull ByteBufAllocator allocator) {
        return new NettyReadBufferFactory(allocator);
    }

    @Override
    public ReadBuffer createEmpty() {
        return new NettyReadBuffer(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public @NonNull ReadBuffer copyOf(@NonNull CharSequence cs, @NonNull Charset charset) {
        ByteBuf byteBuf = charset == StandardCharsets.UTF_8 ?
            ByteBufUtil.writeUtf8(allocator, cs) :
            ByteBufUtil.encodeString(allocator, CharBuffer.wrap(cs), charset);
        return adapt(byteBuf);
    }

    @Override
    public @NonNull ReadBuffer copyOf(@NonNull InputStream stream) throws IOException {
        ByteBuf buffer = allocator.buffer();
        boolean free = true;
        try {
            while (true) {
                if (buffer.writeBytes(stream, 4096) == -1) {
                    break;
                }
            }
            free = false;
            return adapt(buffer);
        } finally {
            if (free) {
                buffer.release();
            }
        }
    }

    @Override
    public @NonNull ReadBuffer copyOf(@NonNull ByteBuffer nioBuffer) {
        ByteBuf bb = allocator.buffer(nioBuffer.remaining());
        boolean done = false;
        try {
            bb.writeBytes(nioBuffer);
            done = true;
            return adapt(bb);
        } finally {
            if (!done) {
                bb.release();
            }
        }
    }

    @Override
    public @NonNull ReadBuffer adapt(@NonNull ByteBuffer nioBuffer) {
        return adapt(Unpooled.wrappedBuffer(nioBuffer));
    }

    @Override
    @NonNull
    public ReadBuffer adapt(@NonNull io.micronaut.core.io.buffer.ByteBuffer<?> buffer) {
        if (buffer.asNativeBuffer() instanceof ByteBuf bb) {
            return adapt(bb);
        }
        return super.adapt(buffer);
    }

    @Override
    public ReadBuffer adapt(byte @NonNull [] array) {
        return adapt(Unpooled.wrappedBuffer(array));
    }

    /**
     * Create a buffer with the given input data. Whether the data is copied or used as-is is
     * implementation-defined. Ownership of the given buffer transfers to this class, so it should
     * not be modified elsewhere after this method is called. Release ownership also transfers to
     * this class.
     *
     * @param buffer A buffer
     * @return The adapted buffer
     */
    @NonNull
    public ReadBuffer adapt(@NonNull ByteBuf buffer) {
        return new NettyReadBuffer(buffer);
    }

    /**
     * Convert the given {@link ReadBuffer} to a netty {@link ByteBuf}. This is a consuming
     * operation.
     *
     * @param readBuffer The buffer to read from
     * @return The read data
     */
    @NonNull
    public static ByteBuf toByteBuf(@NonNull ReadBuffer readBuffer) {
        if (readBuffer instanceof NettyReadBuffer nrb) {
            return nrb.toByteBuf();
        } else {
            return Unpooled.wrappedBuffer(readBuffer.toArray());
        }
    }

    @Override
    public @NonNull <T extends Throwable> ReadBuffer buffer(@NonNull ThrowingConsumer<? super OutputStream, T> writer) throws T {
        ByteBuf buf = allocator.buffer();
        boolean release = true;
        try {
            ByteBufOutputStream s = new ByteBufOutputStream(buf);
            writer.accept(s);
            try {
                s.close();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to close buffer stream", e);
            }
            release = false;
            return adapt(buf);
        } finally {
            if (release) {
                buf.release();
            }
        }
    }

    @Override
    public @NonNull ReadBufferFactory.BufferingOutputStream outputStreamBuffer() {
        return new ReadBufferFactory.BufferingOutputStream() {
            ByteBufOutputStream out = new ByteBufOutputStream(allocator.buffer());

            @Override
            public OutputStream stream() throws IllegalStateException {
                OutputStream out = this.out;
                if (out == null) {
                    throw new IllegalStateException("Already converted to buffer");
                }
                return out;
            }

            @Override
            public ReadBuffer finishBuffer() throws IOException {
                ByteBufOutputStream out = this.out;
                if (out == null) {
                    throw new IllegalStateException("Already converted to buffer");
                }
                this.out = null;
                boolean release = true;
                try {
                    out.close();
                    release = false;
                } finally {
                    if (release) {
                        out.buffer().release();
                    }
                }
                return adapt(out.buffer());
            }

            @Override
            public void close() throws IOException {
                ByteBufOutputStream out = this.out;
                if (out != null) {
                    try {
                        out.close();
                    } finally {
                        out.buffer().release();
                    }
                }
            }
        };
    }

    @Override
    public @NonNull ReadBuffer compose(@NonNull Iterable<@NonNull ReadBuffer> buffers) {
        CompositeByteBuf composite = allocator.compositeBuffer();
        try {
            for (ReadBuffer buffer : buffers) {
                composite.addComponent(true, toByteBuf(buffer));
            }
            return adapt(composite);
        } catch (Throwable e) {
            composite.release();
            for (ReadBuffer buffer : buffers) {
                try {
                    buffer.close();
                } catch (Throwable f) {
                    e.addSuppressed(f);
                }
            }
            throw e;
        }
    }
}
