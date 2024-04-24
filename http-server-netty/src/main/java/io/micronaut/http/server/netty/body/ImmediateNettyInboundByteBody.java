package io.micronaut.http.server.netty.body;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.body.CloseableImmediateInboundByteBody;
import io.micronaut.http.body.ImmediateInboundByteBody;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.Objects;

@Internal
public final class ImmediateNettyInboundByteBody extends NettyInboundByteBody implements CloseableImmediateInboundByteBody {
    @Nullable
    private ByteBuf buffer;

    public ImmediateNettyInboundByteBody(@NonNull ByteBuf buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
    }

    public static CloseableImmediateInboundByteBody empty() {
        return new ImmediateNettyInboundByteBody(Unpooled.EMPTY_BUFFER);
    }

    public static ByteBuf toByteBuf(ImmediateInboundByteBody body) {
        if (body instanceof ImmediateNettyInboundByteBody net) {
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
        ByteBuf b = buffer;
        if (b == null) {
            throw new IllegalStateException("Body already claimed. expectedContentLength() is only allowed before the body is processed.");
        }
        return b.readableBytes();
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
    public @NonNull ExecutionFlow<? extends CloseableImmediateInboundByteBody> buffer() {
        return ExecutionFlow.just(new ImmediateNettyInboundByteBody(claim()));
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
    public @NonNull CloseableImmediateInboundByteBody split() {
        ByteBuf b = buffer;
        if (b == null) {
            failClaim();
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Body split at this location. This is not an error, but may aid in debugging other errors", new Exception());
        }
        return new ImmediateNettyInboundByteBody(b.retainedSlice());
    }
}
