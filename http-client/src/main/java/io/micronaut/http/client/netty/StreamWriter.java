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
package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.stream.LazyUpstream;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.body.StreamingNettyByteBody;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.function.Consumer;

/**
 * This class is used to write from a {@link StreamingNettyByteBody} to a channel with appropriate
 * backpressure control.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Internal
final class StreamWriter extends ChannelInboundHandlerAdapter implements BufferConsumer {
    private final StreamingNettyByteBody body;
    private final Consumer<Throwable> errorHandler;
    private ChannelHandlerContext ctx;
    private EventLoopFlow flow;
    private Upstream upstream;
    private long unwritten = 0;
    private boolean completed = false;

    /**
     * @param body         The body to read from. This {@link StreamWriter} will immediately take ownership of this body.
     * @param errorHandler Handler to call when the streaming body emits an error
     */
    StreamWriter(StreamingNettyByteBody body, Consumer<Throwable> errorHandler) {
        this.body = body;
        this.errorHandler = errorHandler;
    }

    /**
     * Subscribe to the upstream and start writing bytes.
     */
    void startWriting() {
        if (ctx == null) {
            throw new IllegalStateException("Not added to a channel yet");
        }
        LazyUpstream lazyUpstream = new LazyUpstream();
        // primary() can call other methods here immediately, so we need a replacement upstream
        // until it returns
        upstream = lazyUpstream;
        upstream = body.primary(this);
        lazyUpstream.forward(upstream);
        try {
            upstream.start();
        } catch (Exception e) {
            errorHandler.accept(e);
        }
    }

    /**
     * Cancel writing the body (e.g. because a {@code CONTINUE} response was never received).
     */
    void cancel() {
        if (upstream != null) {
            upstream.allowDiscard();
            upstream.disregardBackpressure();
        }
        body.close();
    }

    boolean isCompleted() {
        return completed;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.flow = new EventLoopFlow(ctx.channel().eventLoop());
    }

    @Override
    public void add(ReadBuffer buf) {
        if (flow.executeNow(() -> add0(buf))) {
            add0(buf);
        }
    }

    private void add0(ReadBuffer buf) {
        if (ctx == null) {
            // discarded
            buf.close();
            return;
        }

        int readable = buf.readable();
        ctx.writeAndFlush(new DefaultHttpContent(NettyReadBufferFactory.toByteBuf(buf))).addListener((ChannelFutureListener) future -> {
            assert ctx.executor().inEventLoop();
            if (future.isSuccess()) {
                if (ctx.channel().isWritable()) {
                    upstream.onBytesConsumed(readable);
                } else {
                    unwritten += readable;
                }
            } else {
                error(future.cause());
            }
        });
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        long unwritten = this.unwritten;
        if (ctx.channel().isWritable() && unwritten != 0) {
            this.unwritten = 0;
            upstream.onBytesConsumed(unwritten);
        }
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void complete() {
        if (flow.executeNow(this::complete0)) {
            complete0();
        }
    }

    private void complete0() {
        if (ctx == null) {
            // discarded
            return;
        }

        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.voidPromise());
        completed = true;
    }

    @Override
    public void discard() {
        // explicit cancel requested -> don't call errorHandler
    }

    @Override
    public void error(Throwable e) {
        if (ctx == null) {
            // discarded
            return;
        }

        errorHandler.accept(e);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancel();
    }
}
