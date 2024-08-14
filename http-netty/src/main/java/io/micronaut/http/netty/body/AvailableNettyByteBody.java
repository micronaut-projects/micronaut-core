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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
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

    public static ByteBuf toByteBuf(AvailableByteBody body) {
        if (body instanceof AvailableNettyByteBody net) {
            return net.claim();
        } else {
            return Unpooled.wrappedBuffer(body.toByteArray());
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
            failClaim();
        }
        this.buffer = null;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Body claimed at this location. This is not an error, but may aid in debugging other errors", new Exception());
        }
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
            failClaim();
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Body split at this location. This is not an error, but may aid in debugging other errors", new Exception());
        }
        return new AvailableNettyByteBody(b.retainedSlice());
    }
}
