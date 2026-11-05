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
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A streamed HTTP/2 response written from a thread other than the event loop, while the event loop
 * feeds its body: this is the first event of a server-sent events route that sends from a blocking
 * handler or a scheduler. The writer of the response takes its signals from one thread at a time,
 * so all of them have to come from the event loop, or a signal is lost and the response hangs.
 */
class Http2OffLoopStreamingWriteTest {

    private static final String BODY = "data: one\n\n";

    private final GatedEventLoop serverLoop = new GatedEventLoop();
    private final EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final CompletableFuture<OutboundAccess> access = new CompletableFuture<>();
    private final CompletableFuture<Channel> connection = new CompletableFuture<>();
    private final Client client = new Client();

    @BeforeEach
    void connect() throws Exception {
        Channel serverChannel = new ServerBootstrap()
            .group(serverLoop)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
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
                    }).build());
                }
            })
            .bind(new LocalAddress(Http2OffLoopStreamingWriteTest.class))
            .sync()
            .channel();
        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channel(LocalChannel.class)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), client);
                }
            })
            .connect(serverChannel.localAddress())
            .sync()
            .channel();
        clientChannel.eventLoop().execute(client::request);
    }

    @AfterEach
    void shutdown() throws Exception {
        clientGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        serverLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Test
    void bodyFedByTheEventLoopWhileTheResponseIsWrittenFromAnotherThread() throws Exception {
        OutboundAccess outboundAccess = access.get(10, TimeUnit.SECONDS);
        ByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(BodySizeLimits.UNLIMITED, bytesConsumed -> { });
        AtomicBoolean fed = new AtomicBoolean();
        Runnable feed = () -> {
            if (fed.compareAndSet(false, true)) {
                body.sharedBuffer().add(factory.readBufferFactory().copyOf(BODY, StandardCharsets.UTF_8));
                body.sharedBuffer().complete();
            }
        };
        serverLoop.gate(Thread.currentThread(), feed);

        // not on the event loop
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody());
        serverLoop.execute(feed);

        assertEquals(BODY, client.received.get(10, TimeUnit.SECONDS));
    }

    @Test
    void bodyIsClosedIfTheStreamFinishesBeforeTheWriteReachesTheEventLoop() throws Exception {
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

        // the event loop runs the abort first: the write here does not see it yet
        CountDownLatch release = new CountDownLatch(1);
        serverLoop.execute(() -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        outboundAccess.abort();
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody());
        release.countDown();

        discarded.get(10, TimeUnit.SECONDS);
    }

    /**
     * An event loop that makes the race of the two threads deterministic: when the gated thread
     * submits a signal of an {@link io.micronaut.http.netty.EventLoopFlow}, it has already taken
     * its place in the order of the flow. Before the task is queued, the event loop now feeds the
     * body, so that the signals of the event loop are queued first. This is what happens when the
     * thread is preempted at that point.
     */
    private static final class GatedEventLoop extends SingleThreadIoEventLoop {
        private volatile Thread gatedThread;
        private volatile Runnable beforeFlowTask;

        GatedEventLoop() {
            super(null, new DefaultThreadFactory("gated-loop"), LocalIoHandler.newFactory());
        }

        void gate(Thread thread, Runnable task) {
            beforeFlowTask = task;
            gatedThread = thread;
        }

        @Override
        public void execute(Runnable task) {
            if (Thread.currentThread() == gatedThread && task.getClass().getName().endsWith("EventLoopFlow$Delayed")) {
                CountDownLatch done = new CountDownLatch(1);
                Runnable before = beforeFlowTask;
                super.execute(() -> {
                    try {
                        before.run();
                    } finally {
                        done.countDown();
                    }
                });
                try {
                    if (!done.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The event loop did not feed the body");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            super.execute(task);
        }
    }

    /**
     * Sends one request, and collects the body of the response.
     */
    private static final class Client extends Http2ChannelDuplexHandler {
        final CompletableFuture<String> received = new CompletableFuture<>();
        private final StringBuilder body = new StringBuilder();
        private ChannelHandlerContext context;

        @Override
        protected void handlerAdded0(ChannelHandlerContext ctx) {
            context = ctx;
        }

        void request() {
            context.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                .method("GET").scheme("http").authority("example.com").path("/sse"), true).stream(newStream()));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof Http2DataFrame data) {
                    ByteBuf content = data.content();
                    body.append(content.toString(StandardCharsets.UTF_8));
                    if (data.isEndStream()) {
                        received.complete(body.toString());
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
