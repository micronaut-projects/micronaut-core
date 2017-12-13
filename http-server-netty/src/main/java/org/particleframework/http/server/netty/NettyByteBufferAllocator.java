/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.io.buffer.ByteBufferAllocator;

import javax.inject.Singleton;

/**
 * A {@link ByteBufferAllocator} implementation for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Singleton
class NettyByteBufferAllocator implements ByteBufferAllocator<ByteBufAllocator> {

    private final ByteBufAllocator allocator;

    public NettyByteBufferAllocator() {
        this.allocator = ByteBufAllocator.DEFAULT;
    }

    NettyByteBufferAllocator(ByteBufAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public ByteBufAllocator getNativeAllocator() {
        return allocator;
    }

    @Override
    public ByteBuffer buffer() {
        return new NettyByteBuffer( allocator.buffer() );
    }

    @Override
    public ByteBuffer buffer(int initialCapacity) {
        return new NettyByteBuffer( allocator.buffer(initialCapacity) );
    }

    @Override
    public ByteBuffer buffer(int initialCapacity, int maxCapacity) {
        return new NettyByteBuffer( allocator.buffer(initialCapacity, maxCapacity) );
    }

    @Override
    public ByteBuffer copiedBuffer(byte[] bytes) {
        return new NettyByteBuffer( Unpooled.copiedBuffer(bytes) );
    }
}
