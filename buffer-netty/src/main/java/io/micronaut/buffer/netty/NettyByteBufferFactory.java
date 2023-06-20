/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.util.Send;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/**
 * A {@link ByteBufferFactory} implementation for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Singleton
@BootstrapContextCompatible
public class NettyByteBufferFactory implements ByteBufferFactory<BufferAllocator, Buffer> {

    /**
     * Default Netty ByteBuffer Factory.
     */
    public static final NettyByteBufferFactory DEFAULT = new NettyByteBufferFactory();

    private final Supplier<BufferAllocator> allocatorSupplier;

    /**
     * Default constructor.
     */
    public NettyByteBufferFactory() {
        this.allocatorSupplier = DefaultBufferAllocators::preferredAllocator;
    }

    /**
     * @param allocator The {@link ByteBufAllocator}
     */
    public NettyByteBufferFactory(BufferAllocator allocator) {
        this.allocatorSupplier = () -> allocator;
    }

    @PostConstruct
    final void register(MutableConversionService conversionService) {
        conversionService.addConverter(Buffer.class, ByteBuffer.class, DEFAULT::wrap);
        conversionService.addConverter(ByteBuffer.class, Buffer.class, byteBuffer -> {
            if (byteBuffer instanceof NettyByteBuffer nbb) {
                return nbb.asNativeBuffer();
            }
            throw new IllegalArgumentException("Unconvertible buffer type " + byteBuffer);
        });
    }

    @Override
    public BufferAllocator getNativeAllocator() {
        return allocatorSupplier.get();
    }

    @Override
    public ByteBuffer<Buffer> buffer() {
        return new NettyByteBuffer(allocatorSupplier.get().allocate(0));
    }

    @Override
    public ByteBuffer<Buffer> buffer(int initialCapacity) {
        return new NettyByteBuffer(allocatorSupplier.get().allocate(initialCapacity));
    }

    @Override
    public ByteBuffer<Buffer> buffer(int initialCapacity, int maxCapacity) {
        return new NettyByteBuffer(allocatorSupplier.get().allocate(initialCapacity).implicitCapacityLimit(maxCapacity));
    }

    @Override
    public ByteBuffer<Buffer> copiedBuffer(byte[] bytes) {
        Buffer buffer = allocatorSupplier.get().allocate(bytes.length);
        buffer.writeBytes(bytes);
        return new NettyByteBuffer(buffer);
    }

    @Override
    public ByteBuffer<Buffer> copiedBuffer(java.nio.ByteBuffer nioBuffer) {
        Buffer buffer = allocatorSupplier.get().allocate(nioBuffer.remaining());
        buffer.writeBytes(nioBuffer);
        return new NettyByteBuffer(buffer);
    }

    @Override
    public ByteBuffer<Buffer> wrap(Buffer existing) {
        return new NettyByteBuffer(existing);
    }

    public ByteBuffer<Buffer> wrap(Send<Buffer> existing) {
        return new NettyByteBuffer(existing.receive());
    }

    @Override
    public ByteBuffer<Buffer> wrap(byte[] existing) {
        throw new UnsupportedOperationException(); // todo
    }
}
