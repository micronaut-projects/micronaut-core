/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.jspecify.annotations.Nullable;

/**
 * The first handler of a connection, right before the transport: it records whether bytes were
 * handed to the socket after a given point, so that a failed request write can tell whether any
 * of the request reached the server.
 * <p>Bytes leave through the transport only after a {@code flush} that this handler sees: a
 * write that reached the outbound buffer stays there until the pipeline is flushed, and every
 * {@code flush} of the pipeline passes through its first handler. So when the write of a request
 * head fails and no flush followed a write since the {@linkplain #mark() mark} taken before the
 * request was written, nothing of the request was sent, whatever the failure: the connection was
 * already closed when the head was written, or closed while the head sat unflushed in the
 * outbound buffer. Conversely a flush that followed a write may have sent part of the request,
 * e.g. a request head, or a body, that the peer stopped reading before it closed, so the failure
 * of such a write is not reported as unprocessed. On an HTTP/2 connection the flush may belong
 * to another stream, which errs on the same, safe, side.
 * <p>The state is only touched on the event loop of the connection.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class TransportWriteTracker extends ChannelOutboundHandlerAdapter {
    static final String NAME = "micronaut-transport-write-tracker";

    private long writes;
    private long flushedWrites;

    /**
     * Add a tracker first in the pipeline of a connection, right before the transport, so that
     * it sees every flush, unless it has one already.
     *
     * @param pipeline The pipeline of the connection
     */
    static void addFirst(ChannelPipeline pipeline) {
        if (pipeline.get(TransportWriteTracker.class) == null) {
            pipeline.addFirst(NAME, new TransportWriteTracker());
        }
    }

    /**
     * Find the tracker of the connection a channel belongs to.
     *
     * @param channel The channel of a request: the connection, or a stream of a multiplexed
     *                connection
     * @return The tracker, or {@code null} if the connection has none, e.g. an HTTP/3 stream
     */
    @Nullable
    static TransportWriteTracker find(Channel channel) {
        for (Channel c = channel; c != null; c = c.parent()) {
            TransportWriteTracker tracker = c.pipeline().get(TransportWriteTracker.class);
            if (tracker != null) {
                return tracker;
            }
        }
        return null;
    }

    /**
     * @return A mark of the writes seen so far, to pass to {@link #flushedSince(long)}
     */
    long mark() {
        return writes;
    }

    /**
     * @param mark A {@link #mark()}
     * @return Whether a write made after the mark was flushed, i.e. handed to the transport
     */
    boolean flushedSince(long mark) {
        return flushedWrites > mark;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise);
        if (promise.isDone() && !promise.isSuccess()) {
            // rejected by the transport, e.g. because the connection is already closed: it never
            // entered the outbound buffer, so the next flush cannot send it
            return;
        }
        writes++;
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        flushedWrites = writes;
        ctx.flush();
    }
}
