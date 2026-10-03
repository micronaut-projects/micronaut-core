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
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ByteBodyFactory} implementation with netty-optimized bodies.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class NettyByteBodyFactory extends ByteBodyFactory {
    private final EventLoop loop;

    public NettyByteBodyFactory(Channel channel) {
        this(channel.alloc(), channel.eventLoop());
    }

    NettyByteBodyFactory(ByteBufAllocator alloc, EventLoop loop) {
        super(new NettyByteBufferFactory(alloc), NettyReadBufferFactory.of(alloc));
        this.loop = loop;
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
        // the length first: for an available body it is a claiming operation, like the publisher
        OptionalLong expectedLength = body.expectedLength();
        NettyBodyAdapter adapter = new NettyBodyAdapter(loop, body.toReadBufferPublisher(), null);
        StreamingNettyByteBody.SharedBuffer sb = createStreamingBuffer(BodySizeLimits.UNLIMITED, adapter);
        adapter.setSharedBuffer(sb);
        adapter.setTrailers(body.trailers());
        expectedLength.ifPresent(sb::setExpectedLength);
        return new StreamingNettyByteBody(sb);
    }

    /**
     * Attach the trailing headers of a {@link io.netty.handler.codec.http.LastHttpContent} to a
     * body, see {@link ByteBody#trailers()}.
     *
     * @param body            The body
     * @param trailingHeaders The trailing headers
     * @return The given body if the trailing headers are empty, else a body with the trailers
     * @since 5.3.0
     */
    public CloseableByteBody withTrailers(CloseableByteBody body, HttpHeaders trailingHeaders) {
        if (trailingHeaders.isEmpty()) {
            return body;
        }
        return withTrailers(body, CompletableFuture.completedFuture(new NettyHttpHeaders(trailingHeaders, ConversionService.SHARED)));
    }

    /**
     * Get the trailers of a body that has ended, as netty headers, to send them after its last
     * bytes. A body completes its trailers before it completes its consumers, so they are known
     * when the consumer is completed.
     *
     * @param body The body
     * @return The trailers, or {@code null} if the body carries none, or if they are not known
     * @since 5.3.0
     */
    @Nullable
    public static HttpHeaders trailersToSend(ByteBody body) {
        io.micronaut.http.HttpHeaders headers = knownTrailers(body);
        return headers == null ? null : toNettyHeaders(headers);
    }

    /**
     * Whether a body carries trailers that are already known, e.g. because it was received fully
     * before it is sent on. On HTTP/1 the message of such a body must use the chunked transfer
     * coding even when the length of the body is known: a {@code Content-Length} message cannot
     * carry trailers, and they would be dropped. The trailers of a body created with
     * {@link ByteBodyFactory#withTrailers} may complete later, which is why that body has no
     * expected length at all.
     *
     * @param body The body
     * @return {@code true} if the trailers of the body are known and not empty
     * @since 5.3.0
     */
    public static boolean hasTrailers(ByteBody body) {
        return knownTrailers(body) != null;
    }

    private static io.micronaut.http.@Nullable HttpHeaders knownTrailers(ByteBody body) {
        CompletableFuture<io.micronaut.http.HttpHeaders> trailers = body.trailers().toCompletableFuture();
        if (!trailers.isDone() || trailers.isCompletedExceptionally()) {
            return null;
        }
        io.micronaut.http.HttpHeaders headers = trailers.join();
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers;
    }

    /**
     * Convert headers to netty headers, e.g. the trailers of a body.
     *
     * @param headers The headers
     * @return The netty headers, {@link EmptyHttpHeaders#INSTANCE} if the headers are empty
     * @since 5.3.0
     */
    public static HttpHeaders toNettyHeaders(io.micronaut.http.HttpHeaders headers) {
        if (headers instanceof NettyHttpHeaders nettyHttpHeaders) {
            return nettyHttpHeaders.getNettyHeaders();
        }
        if (headers.isEmpty()) {
            return EmptyHttpHeaders.INSTANCE;
        }
        HttpHeaders nettyHeaders = new DefaultHttpHeaders(false);
        for (String name : headers.names()) {
            nettyHeaders.add(name, headers.getAll(name));
        }
        return nettyHeaders;
    }
}
