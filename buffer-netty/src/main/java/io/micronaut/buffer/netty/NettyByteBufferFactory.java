/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.buffer.netty;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import javax.inject.Singleton;

/**
 * A {@link ByteBufferFactory} implementation for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Singleton
@BootstrapContextCompatible
public class NettyByteBufferFactory implements ByteBufferFactory<ByteBufAllocator, ByteBuf> {

    /**
     * Default Netty ByteBuffer Factory.
     */
    public static final NettyByteBufferFactory DEFAULT = new NettyByteBufferFactory();

    private final ByteBufAllocator allocator;

    static {
        ConversionService.SHARED.addConverter(ByteBuf.class, ByteBuffer.class, DEFAULT::wrap);
        ConversionService.SHARED.addConverter(ByteBuffer.class, ByteBuf.class, byteBuffer -> {
            if (byteBuffer instanceof NettyByteBuffer) {
                return (ByteBuf) byteBuffer.asNativeBuffer();
            }
            throw new IllegalArgumentException("Unconvertible buffer type " + byteBuffer);
        });
    }

    /**
     * Default constructor.
     */
    public NettyByteBufferFactory() {
        this.allocator = ByteBufAllocator.DEFAULT;
    }

    /**
     * @param allocator The {@link ByteBufAllocator}
     */
    public NettyByteBufferFactory(ByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public ByteBufAllocator getNativeAllocator() {
        return allocator;
    }

    @Override
    public ByteBuffer<ByteBuf> buffer() {
        return new NettyByteBuffer(allocator.buffer());
    }

    @Override
    public ByteBuffer<ByteBuf> buffer(int initialCapacity) {
        return new NettyByteBuffer(allocator.buffer(initialCapacity));
    }

    @Override
    public ByteBuffer<ByteBuf> buffer(int initialCapacity, int maxCapacity) {
        return new NettyByteBuffer(allocator.buffer(initialCapacity, maxCapacity));
    }

    @Override
    public ByteBuffer<ByteBuf> copiedBuffer(byte[] bytes) {
        return new NettyByteBuffer(Unpooled.copiedBuffer(bytes));
    }

    @Override
    public ByteBuffer<ByteBuf> copiedBuffer(java.nio.ByteBuffer nioBuffer) {
        return new NettyByteBuffer(Unpooled.copiedBuffer(nioBuffer));
    }

    @Override
    public ByteBuffer<ByteBuf> wrap(ByteBuf existing) {
        return new NettyByteBuffer(existing);
    }

    @Override
    public ByteBuffer<ByteBuf> wrap(byte[] existing) {
        return new NettyByteBuffer(Unpooled.wrappedBuffer(existing));
    }
}
