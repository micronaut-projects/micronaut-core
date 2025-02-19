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
package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.util.functional.ThrowingConsumer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@link ByteBodyFactory} implementation with netty-optimized bodies.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class NettyByteBodyFactory extends ByteBodyFactory {
    public NettyByteBodyFactory(@NonNull Channel channel) {
        // note: atm we only use the alloc from the channel, but in the future we might also use
        // the event loop for streaming bodies. Please design use sites to have a channel
        // available, and don't create a constructor that just takes the alloc :)
        super(new NettyByteBufferFactory(channel.alloc()));
    }

    private ByteBufAllocator alloc() {
        return (ByteBufAllocator) byteBufferFactory().getNativeAllocator();
    }

    @Override
    public @NonNull CloseableAvailableByteBody adapt(@NonNull ByteBuffer<?> buffer) {
        if (buffer.asNativeBuffer() instanceof ByteBuf bb) {
            return new AvailableNettyByteBody(bb);
        }
        return super.adapt(buffer);
    }

    @Override
    public @NonNull CloseableAvailableByteBody adapt(byte @NonNull [] array) {
        return new AvailableNettyByteBody(Unpooled.wrappedBuffer(array));
    }

    @Override
    public @NonNull <T extends Throwable> CloseableAvailableByteBody buffer(@NonNull ThrowingConsumer<? super OutputStream, T> writer) throws T {
        ByteBuf buf = alloc().buffer();
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
            return new AvailableNettyByteBody(buf);
        } finally {
            if (release) {
                buf.release();
            }
        }
    }

    @Override
    public @NonNull CloseableAvailableByteBody createEmpty() {
        return AvailableNettyByteBody.empty();
    }

    @Override
    public @NonNull CloseableAvailableByteBody copyOf(@NonNull CharSequence cs, @NonNull Charset charset) {
        ByteBuf byteBuf = charset == StandardCharsets.UTF_8 ?
            ByteBufUtil.writeUtf8(alloc(), cs) :
            ByteBufUtil.encodeString(alloc(), CharBuffer.wrap(cs), charset);
        return new AvailableNettyByteBody(byteBuf);
    }

    @Override
    public @NonNull CloseableAvailableByteBody copyOf(@NonNull InputStream stream) throws IOException {
        ByteBuf buffer = alloc().buffer();
        boolean free = true;
        try {
            while (true) {
                if (buffer.writeBytes(stream, 4096) == -1) {
                    break;
                }
            }
            free = false;
            return new AvailableNettyByteBody(buffer);
        } finally {
            if (free) {
                buffer.release();
            }
        }
    }
}
