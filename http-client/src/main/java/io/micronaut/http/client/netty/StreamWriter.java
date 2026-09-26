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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * This class is used to write from a {@link StreamingNettyByteBody} to a channel with appropriate
 * backpressure control. It is not a channel handler: the {@link Http1ResponseHandler} of the
 * channel forwards {@link #channelWritabilityChanged() writability changes} for the duration of
 * the request.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Internal
final class StreamWriter implements BufferConsumer {
    private final Channel channel;
    private final EventLoopFlow flow;
    private final StreamingNettyByteBody body;
    private final Consumer<Throwable> errorHandler;
    @Nullable
    private Upstream upstream;
    private long unwritten = 0;
    private boolean completed = false;
    /**
     * Set by {@link #cancel()}. After this point the connection may already be back in the pool
     * and assigned to another request, so queued writes must not touch the channel anymore.
     */
    private volatile boolean closed = false;

    /**
     * @param channel      The channel to write to
     * @param body         The body to read from. This {@link StreamWriter} will immediately take ownership of this body.
     * @param errorHandler Handler to call when the streaming body emits an error
     */
    StreamWriter(Channel channel, StreamingNettyByteBody body, Consumer<Throwable> errorHandler) {
        this.channel = channel;
        this.flow = new EventLoopFlow(channel.eventLoop());
        this.body = body;
        this.errorHandler = errorHandler;
    }

    /**
     * Subscribe to the upstream and start writing bytes.
     */
    void startWriting() {
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
     * Cancel writing the body (e.g. because a {@code CONTINUE} response was never received). Also
     * called when the request is done.
     */
    void cancel() {
        closed = true;
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
    public void add(ReadBuffer buf) {
        if (flow.executeNow(() -> add0(buf))) {
            add0(buf);
        }
    }

    private void add0(ReadBuffer buf) {
        if (closed) {
            // cancelled, the connection may be serving another request already
            buf.close();
            return;
        }

        int readable = buf.readable();
        channel.writeAndFlush(new DefaultHttpContent(NettyReadBufferFactory.toByteBuf(buf))).addListener((ChannelFutureListener) future -> {
            assert channel.eventLoop().inEventLoop();
            if (future.isSuccess()) {
                if (channel.isWritable()) {
                    Objects.requireNonNull(upstream).onBytesConsumed(readable);
                } else {
                    unwritten += readable;
                }
            } else {
                error(future.cause());
            }
        });
    }

    /**
     * Called on the event loop when the writability of the channel changed.
     */
    void channelWritabilityChanged() {
        if (closed) {
            return;
        }
        long unwritten = this.unwritten;
        if (channel.isWritable() && unwritten != 0) {
            this.unwritten = 0;
            Objects.requireNonNull(upstream).onBytesConsumed(unwritten);
        }
    }

    @Override
    public void complete() {
        if (flow.executeNow(this::complete0)) {
            complete0();
        }
    }

    private void complete0() {
        if (closed) {
            // cancelled, the connection may be serving another request already
            return;
        }

        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, channel.voidPromise());
        completed = true;
    }

    @Override
    public void discard() {
        // explicit cancel requested -> don't call errorHandler
    }

    @Override
    public void error(Throwable e) {
        if (closed) {
            // cancelled, the request is already done
            return;
        }
        errorHandler.accept(e);
    }
}
