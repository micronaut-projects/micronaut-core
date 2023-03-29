package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.netty.buffer.ByteBufAllocator;

@Internal
public sealed interface ByteBody extends HttpBody permits ImmediateByteBody, StreamingByteBody {
    MultiObjectBody processMulti(HttpContentProcessor processor) throws Throwable;

    ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc);
}
