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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.ByteBody.SplitBackpressureMode;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An HTTP/1 response written from a thread other than the event loop moves to the event loop: a
 * streamed body is claimed on the calling thread and its response is prepared there, and a full
 * response is handed over once prepared. The event loop may reject the hop, and a failure after it
 * has no caller left to answer it.
 */
class PipeliningMovedWriteTest {

    private static final String REQUEST = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";

    private final RejectingEventLoop serverLoop = new RejectingEventLoop();
    private final EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final BlockingQueue<OutboundAccess> accesses = new LinkedBlockingQueue<>();
    private final CompletableFuture<Channel> connection = new CompletableFuture<>();
    private final List<String> callbacks = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final CompletableFuture<String> received = new CompletableFuture<>();

    @AfterEach
    void shutdown() throws Exception {
        clientGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        serverLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Test
    void aFullBodyIsReleasedIfTheEventLoopRejectsTheWrite() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBuf content = Unpooled.copiedBuffer("hello", StandardCharsets.US_ASCII);
        serverLoop.rejectNext(Thread.currentThread());

        assertThrows(RejectedExecutionException.class,
            () -> outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(content)));

        assertEquals(0, content.refCnt());
    }

    @Test
    void aMovedWriteThatFailsOnTheEventLoopAnswersA500AndClosesTheConnection() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        CompletableFuture<Void> discarded = new CompletableFuture<>();
        ByteBodyFactory.StreamingBody body = streamingBody(discarded::complete);

        // not on the event loop: the headers are prepared there, and fail
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new FailingHeaders()), body.rootBody());

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertTrue(response.toLowerCase().contains("connection: close"), response);
        discarded.get(10, TimeUnit.SECONDS);
        assertCallbacks("failed request", "written request");
        assertInstanceOf(IllegalStateException.class, failures.get(0));
    }

    @Test
    void theAnswerOfAFailedMovedWriteWaitsForTheResponsesBeforeIt() throws Exception {
        OutboundAccess first = connect(2);
        first.attachment("first");
        OutboundAccess second = accesses.poll(10, TimeUnit.SECONDS);
        assertNotNull(second);
        second.attachment("second");
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));

        // the second response fails while the first one is not written yet
        second.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new FailingHeaders()), streamingBody(null).rootBody());
        barrier();
        first.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(Unpooled.copiedBuffer("first", StandardCharsets.US_ASCII)));

        String response = received.get(10, TimeUnit.SECONDS);
        int ok = response.indexOf("HTTP/1.1 200 ");
        int error = response.indexOf("HTTP/1.1 500 ");
        assertTrue(ok >= 0 && error > ok, response);
        assertTrue(response.substring(ok, error).endsWith("first"), response);
        assertCallbacks("failed second", "written first", "written second");
    }

    @Test
    void anInstalledResponseWhoseBodyFailsToStartClosesTheConnection() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBodyFactory.StreamingBody body = factory.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void start() {
                throw new IllegalStateException("cannot start the body");
            }

            @Override
            public void onBytesConsumed(long bytesConsumed) {
                // the body never sends data: there is no producer to resume
            }
        });

        // the handler is installed and has prepared the head when the body fails to start
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody());

        // the cut response cannot be followed by another one: the connection closes
        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(!response.contains("HTTP/1.1 500 "), response);
        assertCallbacks("failed request", "written request");
    }

    @Test
    void aFailingCleanupOfTheBodyDoesNotStopTheAnswer() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        FailingCloseBody body = new FailingCloseBody();

        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new FailingHeaders()), body);

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertCallbacks("failed request", "written request");
        assertEquals(1, body.closes);
        assertEquals("cannot close the body", failures.get(0).getSuppressed()[0].getMessage());
    }

    private ByteBodyFactory.StreamingBody streamingBody(@Nullable Consumer<Void> onDiscard) throws Exception {
        ByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        return factory.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytesConsumed) {
                // the body never sends data: there is no producer to resume
            }

            @Override
            public void allowDiscard() {
                if (onDiscard != null) {
                    onDiscard.accept(null);
                }
            }
        });
    }

    /**
     * The callbacks of the request handler, once everything the event loop had to do is done.
     */
    private void assertCallbacks(String... expected) throws Exception {
        barrier();
        assertEquals(List.of(expected), callbacks);
    }

    private void barrier() throws Exception {
        serverLoop.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private OutboundAccess connect(int requests) throws Exception {
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
                            accesses.add(outboundAccess);
                        }

                        @Override
                        public void handleUnboundError(Throwable cause) {
                            failures.add(cause);
                        }

                        @Override
                        public void responseFailed(Object attachment, Throwable cause) {
                            failures.add(cause);
                            callbacks.add("failed " + attachment);
                        }

                        @Override
                        public void responseWritten(Object attachment) {
                            callbacks.add("written " + attachment);
                        }
                    }));
                }
            })
            .bind(new LocalAddress(PipeliningMovedWriteTest.class))
            .sync()
            .channel();
        StringBuilder bytes = new StringBuilder();
        Channel clientChannel = new Bootstrap()
            .group(clientGroup)
            .channel(LocalChannel.class)
            .handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ByteBuf buf = (ByteBuf) msg;
                    bytes.append(buf.toString(StandardCharsets.US_ASCII));
                    buf.release();
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) {
                    received.complete(bytes.toString());
                }
            })
            .connect(serverChannel.localAddress())
            .sync()
            .channel();
        clientChannel.writeAndFlush(Unpooled.copiedBuffer(REQUEST.repeat(requests), StandardCharsets.US_ASCII));
        OutboundAccess first = accesses.poll(10, TimeUnit.SECONDS);
        assertNotNull(first);
        return first;
    }

    /**
     * Headers that fail when the handler prepares them on the event loop, as a custom compression
     * strategy or a broken header implementation could.
     */
    private static final class FailingHeaders extends DefaultHttpHeaders {
        @Override
        public HttpHeaders remove(CharSequence name) {
            if (Thread.currentThread().getName().startsWith("rejecting-loop")) {
                throw new IllegalStateException("cannot prepare the headers");
            }
            return super.remove(name);
        }
    }

    /**
     * A streamed body whose cleanup fails. The response fails before it reads the body, so only
     * its claim and its close are reached.
     */
    private static final class FailingCloseBody implements CloseableByteBody {
        volatile int closes;

        @Override
        public void close() {
            closes++;
            throw new IllegalStateException("cannot close the body");
        }

        @Override
        public CloseableByteBody move() {
            return this;
        }

        @Override
        public OptionalLong expectedLength() {
            return OptionalLong.empty();
        }

        @Override
        public CloseableByteBody split(SplitBackpressureMode backpressureMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream toInputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Publisher<byte[]> toByteArrayPublisher() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<? extends CloseableAvailableByteBody> buffer() {
            throw new UnsupportedOperationException();
        }
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
