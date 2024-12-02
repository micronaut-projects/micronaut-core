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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Netty ByteBuf implementation of ImmediateByteBody.
 *
 * @since 4.5.0
 * @author Jonas Konrad
 */
@Internal
public final class AvailableNettyByteBody extends NettyByteBody implements CloseableAvailableByteBody {
    private final long length;
    @Nullable
    private ByteBuf buffer;

    public AvailableNettyByteBody(@NonNull ByteBuf buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.length = buffer.readableBytes();
    }

    public static CloseableAvailableByteBody empty() {
        return new AvailableNettyByteBody(Unpooled.EMPTY_BUFFER);
    }

    @NonNull
    public static ByteBuf toByteBuf(@NonNull AvailableByteBody body) {
        if (body instanceof AvailableNettyByteBody net) {
            return net.claim();
        } else {
            return Unpooled.wrappedBuffer(body.toByteArray());
        }
    }

    /**
     * This is a wrapper around {@link AvailableNettyByteBody#AvailableNettyByteBody(ByteBuf)}
     * with an extra body length check.
     *
     * @param loop The event loop for constructing {@link StreamingNettyByteBody}
     * @param bodySizeLimits The body size limits to check
     * @param buf The input buffer
     * @return The body with the given input buffer, or a {@link StreamingNettyByteBody} with the
     * appropriate content length error
     */
    @NonNull
    public static CloseableByteBody createChecked(@NonNull EventLoop loop, @NonNull BodySizeLimits bodySizeLimits, @NonNull ByteBuf buf) {
        // AvailableNettyByteBody does not support exceptions, so if we hit one of the configured
        // limits, we return a StreamingNettyByteBody instead.
        int readable = buf.readableBytes();
        if (readable > bodySizeLimits.maxBodySize() || readable > bodySizeLimits.maxBufferSize()) {
            BufferConsumer.Upstream upstream = bytesConsumed -> {
            };
            StreamingNettyByteBody.SharedBuffer mockBuffer = new StreamingNettyByteBody.SharedBuffer(loop, bodySizeLimits, upstream);
            mockBuffer.add(buf); // this will trigger the exception for exceeded body or buffer size
            return new StreamingNettyByteBody(mockBuffer);
        } else {
            return new AvailableNettyByteBody(buf);
        }
    }

    public ByteBuf peek() {
        ByteBuf b = buffer;
        if (b == null) {
            throw new IllegalStateException("Body already claimed.");
        }
        return b;
    }

    @Override
    public @NonNull InputStream toInputStream() {
        return new ByteBufInputStream(claim(), true);
    }

    @Override
    public long length() {
        return length;
    }

    @NonNull
    private ByteBuf claim() {
        ByteBuf b = buffer;
        if (b == null) {
            BaseSharedBuffer.failClaim();
        }
        this.buffer = null;
        BaseSharedBuffer.logClaim();
        return b;
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        return ExecutionFlow.just(new AvailableNettyByteBody(claim()));
    }

    @Override
    public void close() {
        ByteBuf b = buffer;
        this.buffer = null;
        if (b != null) {
            b.release();
        }
    }

    @Override
    protected Flux<ByteBuf> toByteBufPublisher() {
        return Flux.just(claim());
    }

    @Override
    public byte @NonNull [] toByteArray() {
        ByteBuf b = claim();
        try {
            return ByteBufUtil.getBytes(b);
        } finally {
            b.release();
        }
    }

    @Override
    public @NonNull ByteBuffer<?> toByteBuffer() {
        return NettyByteBufferFactory.DEFAULT.wrap(claim());
    }

    @Override
    public @NonNull CloseableByteBody move() {
        return new AvailableNettyByteBody(claim());
    }

    @Override
    public @NonNull String toString(Charset charset) {
        ByteBuf b = claim();
        try {
            return b.toString(charset);
        } finally {
            b.release();
        }
    }

    @Override
    public @NonNull CloseableAvailableByteBody split() {
        ByteBuf b = buffer;
        if (b == null) {
            BaseSharedBuffer.failClaim();
        }
        return new AvailableNettyByteBody(b.retainedSlice());
    }
}
