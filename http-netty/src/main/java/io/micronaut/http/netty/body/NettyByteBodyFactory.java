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
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaders;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * {@link ByteBodyFactory} implementation with netty-optimized bodies.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class NettyByteBodyFactory extends ByteBodyFactory {
    private final EventLoop loop;

    public NettyByteBodyFactory(@NonNull Channel channel) {
        this(channel.alloc(), channel.eventLoop());
    }

    NettyByteBodyFactory(ByteBufAllocator alloc, EventLoop loop) {
        super(new NettyByteBufferFactory(alloc), NettyReadBufferFactory.of(alloc));
        this.loop = loop;
    }

    @Override
    public @NonNull NettyReadBufferFactory readBufferFactory() {
        return (NettyReadBufferFactory) super.readBufferFactory();
    }

    public CloseableAvailableByteBody adapt(ByteBuf byteBuf) {
        return adapt(readBufferFactory().adapt(byteBuf));
    }

    public CloseableByteBody createChecked(@NonNull BodySizeLimits bodySizeLimits, @NonNull ByteBuf buf) {
        // AvailableNettyByteBody does not support exceptions, so if we hit one of the configured
        // limits, we return a StreamingNettyByteBody instead.
        int readable = buf.readableBytes();
        if (readable > bodySizeLimits.maxBodySize() || readable > bodySizeLimits.maxBufferSize()) {
            BufferConsumer.Upstream upstream = bytesConsumed -> {
            };
            StreamingNettyByteBody.SharedBuffer mockBuffer = createStreamingBuffer(bodySizeLimits, upstream);
            mockBuffer.add(buf); // this will trigger the exception for exceeded body or buffer size
            return new StreamingNettyByteBody(mockBuffer);
        } else {
            return adapt(buf);
        }
    }

    public CloseableByteBody adapt(Publisher<ByteBuf> publisher) {
        return adapt(publisher, null, null);
    }

    public CloseableByteBody adapt(Publisher<ByteBuf> publisher, @Nullable HttpHeaders headersForLength, @Nullable Runnable onDiscard) {
        return NettyBodyAdapter.adapt(publisher, loop, this, headersForLength, onDiscard);
    }

    public static CloseableAvailableByteBody empty() {
        return AvailableByteArrayBody.create(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT).createEmpty());
    }

    public static ByteBuf toByteBuf(AvailableByteBody body) {
        try (ReadBuffer rb = body.toReadBuffer()) {
            return NettyReadBufferFactory.toByteBuf(rb);
        }
    }

    public static Flux<ByteBuf> toByteBufs(ByteBody body) {
        return Flux.from(body.toReadBufferPublisher())
            .map(NettyReadBufferFactory::toByteBuf);
    }

    public StreamingNettyByteBody.SharedBuffer createStreamingBuffer(BodySizeLimits limits, BufferConsumer.Upstream rootUpstream) {
        return new StreamingNettyByteBody.SharedBuffer(loop, this, limits, rootUpstream);
    }

    public StreamingNettyByteBody toStreaming(ByteBody body) {
        if (body instanceof StreamingNettyByteBody snbb) {
            return snbb;
        }
        return NettyBodyAdapter.adaptStreaming(body, loop, this);
    }
}
