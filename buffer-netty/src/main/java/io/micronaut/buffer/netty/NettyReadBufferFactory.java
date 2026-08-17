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

import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.util.functional.ThrowingConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

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
    public static NettyReadBufferFactory of(ByteBufAllocator allocator) {
        return new NettyReadBufferFactory(allocator);
    }

    @Override
    public ReadBuffer createEmpty() {
        return adapt(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public ReadBuffer copyOf(CharSequence cs, Charset charset) {
        ByteBuf byteBuf = charset == StandardCharsets.UTF_8 ?
            ByteBufUtil.writeUtf8(allocator, cs) :
            ByteBufUtil.encodeString(allocator, CharBuffer.wrap(cs), charset);
        return adapt(byteBuf);
    }

    @Override
    public ReadBuffer copyOf(InputStream stream) throws IOException {
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
    public @Nullable ReadBuffer copyOf(ScatteringByteChannel channel, int n) throws IOException {
        ByteBuf bb = allocator.buffer(n);
        int actual;
        try {
            actual = bb.writeBytes(channel, n);
        } catch (Throwable e) {
            bb.release();
            throw e;
        }
        if (actual < 0) {
            bb.release();
            return null;
        } else if (actual > 0) {
            return adapt(bb);
        } else {
            bb.release();
            return createEmpty();
        }
    }

    @Override
    public ReadBuffer copyOf(ByteBuffer nioBuffer) {
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
    public ReadBuffer adapt(ByteBuffer nioBuffer) {
        return adapt(Unpooled.wrappedBuffer(nioBuffer));
    }

    @Override
    public ReadBuffer adapt(io.micronaut.core.io.buffer.ByteBuffer<?> buffer) {
        if (buffer.asNativeBuffer() instanceof ByteBuf bb) {
            return adapt(bb);
        }
        return super.adapt(buffer);
    }

    @Override
    public ReadBuffer adapt(byte[] array) {
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
    public ReadBuffer adapt(ByteBuf buffer) {
        return new NettyReadBuffer(buffer);
    }

    /**
     * Convert the given {@link ReadBuffer} to a netty {@link ByteBuf}. This is a consuming
     * operation.
     *
     * @param readBuffer The buffer to read from
     * @return The read data
     */
    public static ByteBuf toByteBuf(ReadBuffer readBuffer) {
        if (readBuffer instanceof NettyReadBuffer nrb) {
            return nrb.toByteBuf();
        } else {
            return Unpooled.wrappedBuffer(readBuffer.toArray());
        }
    }

    @Override
    public <T extends Throwable> ReadBuffer buffer(ThrowingConsumer<? super OutputStream, T> writer) throws T {
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
    public ReadBufferFactory. BufferingOutputStream outputStreamBuffer() {
        return new ReadBufferFactory.BufferingOutputStream() {
            @Nullable
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
    public ReadBuffer compose(Iterable<ReadBuffer> buffers) {
        // shortcuts for buffers.size == 0 or 1
        Iterator<ReadBuffer> itr = buffers.iterator();
        if (!itr.hasNext()) {
            return createEmpty();
        } else {
            ReadBuffer first = itr.next();
            if (!itr.hasNext()) {
                return first;
            }
        }
        // toByteBuf consumes each ReadBuffer, so if extraction fails partway, the ByteBufs
        // already extracted have no owner anymore and must be released explicitly here.
        List<ByteBuf> components = buffers instanceof Collection<?> collection
            ? new ArrayList<>(collection.size())
            : new ArrayList<>();
        try {
            for (ReadBuffer buffer : buffers) {
                components.add(toByteBuf(buffer));
            }
        } catch (Throwable e) {
            for (ByteBuf component : components) {
                ReferenceCountUtil.safeRelease(component);
            }
            for (ReadBuffer buffer : buffers) {
                try {
                    buffer.close();
                } catch (Throwable f) {
                    e.addSuppressed(f);
                }
            }
            throw e;
        }
        CompositeByteBuf composite = allocator.compositeBuffer();
        try {
            // addComponents consolidates at most once, at the end. Adding components one at a time
            // calls consolidateIfNeeded() after each one, and each consolidation copies everything
            // accumulated so far, making aggregation O(size^2 / chunkSize). addComponents also
            // takes ownership of all components, releasing any it did not add, so from here on the
            // composite is the only thing left to release.
            composite.addComponents(true, components);
            return adapt(composite);
        } catch (Throwable e) {
            composite.release();
            throw e;
        }
    }
}
