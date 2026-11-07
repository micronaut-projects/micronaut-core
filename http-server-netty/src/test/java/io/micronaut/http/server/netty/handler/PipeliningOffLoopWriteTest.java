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

import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A streamed HTTP/1 response written from a thread other than the event loop, which claims the
 * body before it hands the write to the event loop.
 */
class PipeliningOffLoopWriteTest {

    private final RejectingEventLoop serverLoop = new RejectingEventLoop();
    private final EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());

    @AfterEach
    void shutdown() throws Exception {
        clientGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        serverLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Test
    void bodyIsClosedIfTheEventLoopRejectsTheWrite() throws Exception {
        CompletableFuture<OutboundAccess> access = new CompletableFuture<>();
        CompletableFuture<Channel> connection = new CompletableFuture<>();
        Channel serverChannel = new ServerBootstrap()
            .group(serverLoop)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new HttpServerCodec(), new PipeliningServerHandler(new RequestHandler() {
                        @Override
                        public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                            body.close();
                            connection.complete(ctx.channel());
                            access.complete(outboundAccess);
                        }

                        @Override
                        public void handleUnboundError(Throwable cause) {
                            access.completeExceptionally(cause);
                        }
                    }));
                }
            })
            .bind(new LocalAddress(PipeliningOffLoopWriteTest.class))
            .sync()
            .channel();
        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channel(LocalChannel.class)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                }
            })
            .connect(serverChannel.localAddress())
            .sync()
            .channel();
        clientChannel.writeAndFlush(Unpooled.copiedBuffer("GET / HTTP/1.1\r\nHost: example.com\r\n\r\n", StandardCharsets.US_ASCII));

        OutboundAccess outboundAccess = access.get(10, TimeUnit.SECONDS);
        ByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        CompletableFuture<Void> discarded = new CompletableFuture<>();
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            public void allowDiscard() {
                discarded.complete(null);
            }
        });
        serverLoop.rejectNext(Thread.currentThread());

        assertThrows(RejectedExecutionException.class,
            () -> outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody()));

        discarded.get(10, TimeUnit.SECONDS);
    }

    /**
     * An event loop that rejects one task of a given thread, as one that shut down does.
     */
    private static final class RejectingEventLoop extends SingleThreadIoEventLoop {
        private volatile Thread rejectedThread;

        RejectingEventLoop() {
            super(null, new DefaultThreadFactory("rejecting-loop"), LocalIoHandler.newFactory());
        }

        void rejectNext(Thread thread) {
            rejectedThread = thread;
        }

        @Override
        public void execute(Runnable task) {
            if (Thread.currentThread() == rejectedThread) {
                rejectedThread = null;
                throw new RejectedExecutionException("event executor terminated");
            }
            super.execute(task);
        }
    }
}
