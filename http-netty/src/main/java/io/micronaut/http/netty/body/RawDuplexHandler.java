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
package io.micronaut.http.netty.body;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.stream.LazyUpstream;
import io.micronaut.http.netty.EventLoopFlow;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The last handler of a connection that switched protocols ({@code 101 Switching Protocols}):
 * the raw bytes the peer sends are {@link #inbound()}, a streaming body read with backpressure,
 * and {@link #send(CloseableByteBody)} writes a body to the peer. The connection is closed when
 * the sent body ends or fails, when the consumer of the inbound bytes gives up, when the peer
 * closes, or on an {@link IdleStateEvent} of an activity timeout in front of this handler.
 * Once the connection is inactive, {@code onClosed} runs once.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RawDuplexHandler extends ChannelInboundHandlerAdapter implements BufferConsumer.Upstream {
    /**
     * The name of this handler in a pipeline.
     */
    public static final String NAME = "raw-duplex";

    private final Channel channel;
    private final EventLoop loop;
    private final StreamingNettyByteBody.SharedBuffer inbound;
    private final CloseableByteBody inboundBody;
    private final Runnable onClosed;
    @Nullable
    private ChannelHandlerContext ctx;
    private long demand;
    private boolean inboundDone;
    private boolean closed;
    @Nullable
    private RawWriter writer;

    /**
     * @param channel  The channel
     * @param onClosed Runs once, when the connection is inactive
     */
    public RawDuplexHandler(Channel channel, Runnable onClosed) {
        this.channel = channel;
        this.loop = channel.eventLoop();
        this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
        this.inbound = new NettyByteBodyFactory(channel).createStreamingBuffer(BodySizeLimits.UNLIMITED, this);
        this.inboundBody = new StreamingNettyByteBody(inbound);
    }

    /**
     * The bytes the peer sends after the switch. Reading them drives the reads of the
     * connection; closing the body without reading it closes the connection.
     *
     * @return The inbound bytes
     */
    public CloseableByteBody inbound() {
        return inboundBody;
    }

    /**
     * Send a body to the peer. Its ownership transfers to the connection, which closes it when
     * it is sent or when the connection is closed; when the body ends or fails, the connection
     * is closed. Can be called once.
     *
     * @param outbound The bytes to send
     */
    public void send(CloseableByteBody outbound) {
        Objects.requireNonNull(outbound, "outbound");
        if (loop.inEventLoop()) {
            send0(outbound);
        } else {
            loop.execute(() -> send0(outbound));
        }
    }

    private void send0(CloseableByteBody outbound) {
        if (writer != null) {
            outbound.close();
            throw new IllegalStateException("A body is already being sent");
        }
        if (closed) {
            outbound.close();
            return;
        }
        writer = new RawWriter(new NettyByteBodyFactory(channel).toStreaming(outbound));
        writer.start();
    }

    /**
     * Close the connection.
     */
    public void close() {
        channel.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        // reads are driven by the consumer of the inbound bytes
        ctx.channel().config().setAutoRead(false);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            int n = buf.readableBytes();
            if (inboundDone || n == 0) {
                buf.release();
                return;
            }
            // bytes that arrive before the consumer of the inbound bytes subscribed, e.g. the bytes of the
            // new protocol the HTTP codec read together with the 101, which it passes on when it is removed,
            // are buffered by the shared buffer until then, and count against the demand
            demand -= n;
            inbound.add(NettyReadBufferFactory.of(ctx.alloc()).adapt(buf));
        } else {
            // the HTTP objects of the 101 that the codec still emits, e.g. its empty last content
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (demand > 0 && !inboundDone) {
            ctx.read();
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (writer != null) {
            writer.writable();
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            // the activity timeout of the switched protocol
            ctx.close();
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        finish(null);
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        finish(cause);
        ctx.close();
    }

    /**
     * The connection is over: end the inbound bytes, stop sending, and notify once.
     */
    private void finish(@Nullable Throwable cause) {
        if (closed) {
            return;
        }
        closed = true;
        if (!inboundDone) {
            inboundDone = true;
            if (cause == null) {
                inbound.complete();
            } else {
                inbound.error(cause);
            }
        }
        if (writer != null) {
            writer.cancel();
        }
        onClosed.run();
    }

    // the consumer of the inbound bytes drives the reads, like a streaming response body

    @Override
    public void start() {
        onBytesConsumed(1);
    }

    @Override
    public void onBytesConsumed(long bytesConsumed) {
        if (loop.inEventLoop()) {
            onBytesConsumed0(bytesConsumed);
        } else {
            loop.execute(() -> onBytesConsumed0(bytesConsumed));
        }
    }

    private void onBytesConsumed0(long bytesConsumed) {
        if (inboundDone || ctx == null) {
            return;
        }
        long oldDemand = demand;
        long newDemand = oldDemand + bytesConsumed;
        if (newDemand < oldDemand) {
            // overflow
            newDemand = Long.MAX_VALUE;
        }
        demand = newDemand;
        if (oldDemand <= 0 && newDemand > 0) {
            ctx.read();
        }
    }

    @Override
    public void allowDiscard() {
        // the consumer of the peer's bytes gave up: the switched protocol is over
        channel.close();
    }

    @Override
    public void disregardBackpressure() {
        onBytesConsumed(Long.MAX_VALUE);
    }

    /**
     * Writes a streaming body to the channel as raw bytes, with backpressure from the channel
     * writability.
     */
    private final class RawWriter implements BufferConsumer {
        private final StreamingNettyByteBody body;
        private final EventLoopFlow flow = new EventLoopFlow(loop);
        @Nullable
        private Upstream upstream;
        @Nullable
        private ChannelFuture lastWrite;
        private long unwritten;
        /**
         * Whether the body ended, failed or is cancelled: bytes that still arrive are dropped.
         */
        private boolean done;
        /**
         * Whether the body was released. Apart from {@link #done}, since a write that fails
         * after the body ended, or while it was still sending, must still release it.
         */
        private boolean released;

        RawWriter(StreamingNettyByteBody body) {
            this.body = body;
        }

        void start() {
            LazyUpstream lazyUpstream = new LazyUpstream();
            // primary() can call other methods here immediately, so we need a replacement upstream
            // until it returns
            upstream = lazyUpstream;
            upstream = body.primary(this);
            lazyUpstream.forward(upstream);
            try {
                upstream.start();
            } catch (Exception e) {
                error(e);
            }
        }

        void writable() {
            long n = unwritten;
            if (n != 0 && channel.isWritable() && upstream != null) {
                unwritten = 0;
                upstream.onBytesConsumed(n);
            }
        }

        void cancel() {
            done = true;
            if (released) {
                return;
            }
            released = true;
            if (upstream != null) {
                upstream.allowDiscard();
                upstream.disregardBackpressure();
            }
            body.close();
        }

        @Override
        public void add(ReadBuffer buf) {
            if (flow.executeNow(() -> add0(buf))) {
                add0(buf);
            }
        }

        private void add0(ReadBuffer buf) {
            if (done) {
                buf.close();
                return;
            }
            int n = buf.readable();
            ChannelFuture write = channel.writeAndFlush(NettyReadBufferFactory.toByteBuf(buf));
            lastWrite = write;
            write.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    if (channel.isWritable()) {
                        Objects.requireNonNull(upstream).onBytesConsumed(n);
                    } else {
                        unwritten += n;
                    }
                } else {
                    error(future.cause());
                }
            });
        }

        @Override
        public void complete() {
            if (flow.executeNow(this::complete0)) {
                complete0();
            }
        }

        private void complete0() {
            // the bytes to the peer ended, i.e. the other side of the relay closed: end the connection, but
            // only once the last of them is written, a close would drop the writes still queued
            done = true;
            ChannelFuture last = lastWrite;
            if (last == null) {
                channel.close();
            } else {
                last.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void error(Throwable e) {
            if (flow.executeNow(() -> error0(e))) {
                error0(e);
            }
        }

        private void error0(Throwable e) {
            // a write failed, or the body did: release the body first, whose source may still
            // be sending, also when the connection was already finished
            cancel();
            finish(e);
            channel.close();
        }
    }
}
