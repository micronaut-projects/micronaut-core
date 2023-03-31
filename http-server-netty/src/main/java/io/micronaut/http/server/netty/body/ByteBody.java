package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Base class for a raw {@link HttpBody} with just bytes.
 */
@Internal
public sealed interface ByteBody extends HttpBody permits ImmediateByteBody, StreamingByteBody {
    /**
     * Process this body using the given processor.
     *
     * @param processor The processor to apply
     * @return The new processed body
     * @throws Throwable Any exception thrown by the processor. Not all processing failures may
     * throw immediately, however
     */
    MultiObjectBody processMulti(HttpContentProcessor processor) throws Throwable;

    /**
     * Fully buffer this body.
     *
     * @param alloc The allocator for storage
     * @return A flow that completes when all data has been read
     */
    ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc);

    /**
     * Create a byte body for the given request. The request must be either a
     * {@link FullHttpRequest} or a {@link StreamedHttpRequest}.
     *
     * @param request The request
     * @return The {@link ByteBody} for the body data
     */
    static ByteBody of(HttpRequest request) {
        if (request instanceof FullHttpRequest full) {
            return new ImmediateByteBody(full.content());
        } else {
            return new StreamingByteBody((StreamedHttpRequest) request);
        }
    }
}
