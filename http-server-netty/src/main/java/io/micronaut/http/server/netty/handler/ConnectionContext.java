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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;

/**
 * What a server handler knows about its connection once it is added to the pipeline. The
 * handler creates it in {@code handlerAdded} and hands it to its per-request and per-stream
 * objects, which keep it in a final field instead of reading the handler's fields.
 *
 * @param ctx            The context of the handler
 * @param eventLoop      The event loop of the channel
 * @param flushCoalescer Coalesces the flushes requested outside a read
 * @since 5.3.0
 */
@Internal
record ConnectionContext(ChannelHandlerContext ctx, EventLoop eventLoop, FlushCoalescer flushCoalescer) {

    /**
     * @param ctx   The context of the handler, which is registered already
     * @param flush The flush that the coalescer runs
     * @return The connection context
     */
    static ConnectionContext of(ChannelHandlerContext ctx, Runnable flush) {
        return new ConnectionContext(ctx, ctx.channel().eventLoop(), new FlushCoalescer(ctx.executor(), flush));
    }

    /**
     * @return The channel
     */
    Channel channel() {
        return ctx.channel();
    }

    /**
     * The allocator of the channel. It is read from the channel configuration on each call, like
     * {@link ChannelHandlerContext#alloc()}, since the configuration may still change.
     *
     * @return The allocator
     */
    ByteBufAllocator alloc() {
        return ctx.alloc();
    }
}
