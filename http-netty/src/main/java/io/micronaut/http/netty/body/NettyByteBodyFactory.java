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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.AbstractBodyAdapter;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.jspecify.annotations.Nullable;
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
    private static final AttributeKey<NettyByteBodyFactory> CHANNEL_FACTORY = AttributeKey.valueOf(NettyByteBodyFactory.class, "channelFactory");

    private final EventLoop loop;

    public NettyByteBodyFactory(Channel channel) {
        this(channel.alloc(), channel.eventLoop());
    }

    NettyByteBodyFactory(ByteBufAllocator alloc, EventLoop loop) {
        super(new NettyByteBufferFactory(alloc), NettyReadBufferFactory.of(alloc));
        this.loop = loop;
    }

    /**
     * Get the factory shared by all users of the given channel. A factory only carries the
     * channel's allocator and event loop, both of which are fixed once the channel is registered,
     * so one instance can serve every request and every body on that channel. The instance is
     * created on first access and stored as a channel attribute.
     *
     * @param channel The channel
     * @return The shared factory for the channel
     * @since 5.2.0
     */
    public static NettyByteBodyFactory forChannel(Channel channel) {
        Attribute<NettyByteBodyFactory> attribute = channel.attr(CHANNEL_FACTORY);
        NettyByteBodyFactory factory = attribute.get();
        if (factory == null) {
            factory = new NettyByteBodyFactory(channel);
            NettyByteBodyFactory existing = attribute.setIfAbsent(factory);
            if (existing != null) {
                factory = existing;
            }
        }
        return factory;
    }

    @Override
    public NettyReadBufferFactory readBufferFactory() {
        return (NettyReadBufferFactory) super.readBufferFactory();
    }

    @Override
    public StreamingBody createStreamingBody(BodySizeLimits limits, BufferConsumer.Upstream upstream) {
        StreamingNettyByteBody.SharedBuffer sb = createStreamingBuffer(limits, upstream);
        return new StreamingBody(sb, new StreamingNettyByteBody(sb));
    }

    @Override
    protected AbstractBodyAdapter createBodyAdapter(Publisher<ReadBuffer> publisher, @Nullable Runnable onDiscard) {
        return new NettyBodyAdapter(loop, publisher, onDiscard);
    }

    public CloseableAvailableByteBody adapt(ByteBuf byteBuf) {
        return adapt(readBufferFactory().adapt(byteBuf));
    }

    public CloseableByteBody createChecked(BodySizeLimits bodySizeLimits, ByteBuf buf) {
        return createChecked(bodySizeLimits, readBufferFactory().adapt(buf));
    }

    public CloseableByteBody adaptNetty(Publisher<ByteBuf> publisher) {
        return adapt(publisher, null, null);
    }

    public CloseableByteBody adapt(Publisher<ByteBuf> publisher, @Nullable HttpHeaders headersForLength, @Nullable Runnable onDiscard) {
        return adapt(
            Flux.from(publisher).map(readBufferFactory()::adapt),
            BodySizeLimits.UNLIMITED, headersForLength == null ? null : new NettyHttpHeaders(headersForLength, ConversionService.SHARED),
            onDiscard);
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

    @Override
    public StreamingNettyByteBody toStreaming(ByteBody body) {
        if (body instanceof StreamingNettyByteBody snbb && snbb.isCompatible(loop)) {
            return snbb;
        }
        NettyBodyAdapter adapter = new NettyBodyAdapter(loop, body.toReadBufferPublisher(), null);
        StreamingNettyByteBody.SharedBuffer sb = createStreamingBuffer(BodySizeLimits.UNLIMITED, adapter);
        adapter.setSharedBuffer(sb);
        body.expectedLength().ifPresent(sb::setExpectedLength);
        return new StreamingNettyByteBody(sb);
    }
}
