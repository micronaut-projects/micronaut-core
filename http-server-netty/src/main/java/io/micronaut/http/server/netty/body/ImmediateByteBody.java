package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully buffered {@link ByteBody}, all operations are eager.
 */
@Internal
public final class ImmediateByteBody extends ManagedBody<ByteBuf> implements ByteBody {
    private final boolean empty;

    ImmediateByteBody(ByteBuf buf) {
        super(buf);
        this.empty = !buf.isReadable();
    }

    @Override
    void release(ByteBuf value) {
        value.release();
    }

    @Override
    public ImmediateMultiObjectBody processMulti(HttpContentProcessor processor) throws Throwable {
        List<Object> out = new ArrayList<>(1);
        ByteBuf data = prepareClaim();
        if (data.isReadable()) {
            processor.add(new DefaultHttpContent(data), out);
        } else {
            data.release();
        }
        processor.complete(out);
        return next(new ImmediateMultiObjectBody(out));
    }

    @Override
    public ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc) {
        return ExecutionFlow.just(this);
    }

    public boolean empty() {
        return empty;
    }
}
