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

import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
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
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A full HTTP/2 response whose final write cannot get its promise after the headers were
 * submitted: the body is released, and no DATA frame follows the headers.
 */
class Http2FinalWritePromiseFailureTest {

    private static final String PROMISE_FAILURE = "cannot create the promise of the final write";

    private final EventLoopGroup serverGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final CompletableFuture<ByteBuf> responseBody = new CompletableFuture<>();
    private final CompletableFuture<Throwable> writeFailure = new CompletableFuture<>();
    private final Client client = new Client();
    private Channel clientChannel;

    @BeforeEach
    void connect() throws Exception {
        Channel serverChannel = new ServerBootstrap()
            .group(serverGroup)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
                        @Override
                        public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                            body.close();
                            writeWithFailingFinalPromise(ctx, outboundAccess);
                        }

                        @Override
                        public void handleUnboundError(Throwable cause) {
                            writeFailure.completeExceptionally(cause);
                        }

                        @Override
                        public void responseWritten(Object attachment) {
                            // the test checks the body and the frames, not the callbacks
                        }
                    }).decompress(false).build());
                }
            })
            .bind(new LocalAddress(Http2FinalWritePromiseFailureTest.class))
            .sync()
            .channel();
        clientChannel = new Bootstrap()
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
        serverGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    /**
     * Write a non-empty full response with its final write recorded, on the event loop, while
     * the handler's context cannot create promises: the headers go out with a void promise, the
     * recorded final write then fails to get its promise.
     */
    private void writeWithFailingFinalPromise(ChannelHandlerContext ctx, OutboundAccess outboundAccess) {
        Http2ServerHandler handler = (Http2ServerHandler) ((Http2ConnectionHandler) ctx.handler()).decoder().frameListener();
        handler.ctx = new NewPromiseFailingContext(ctx);
        ByteBuf content = Unpooled.copiedBuffer("hello world", StandardCharsets.UTF_8);
        try {
            outboundAccess.recordFinalWrite();
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adapt(content));
            writeFailure.complete(null);
        } catch (RuntimeException e) {
            writeFailure.complete(e);
        } finally {
            handler.ctx = ctx;
            // send what was written, the headers, to the client
            ctx.flush();
            responseBody.complete(content);
        }
    }

    @Test
    void theBodyIsReleasedWhenTheFinalWriteCannotGetItsPromiseAfterTheHeaders() throws Exception {
        Throwable failure = writeFailure.get(10, TimeUnit.SECONDS);
        assertEquals(PROMISE_FAILURE, failure == null ? null : failure.getMessage());
        assertEquals(0, responseBody.get(10, TimeUnit.SECONDS).refCnt());

        assertEquals("status 200", client.headers.get(10, TimeUnit.SECONDS));
        // the ping is answered after everything the server wrote before it
        clientChannel.eventLoop().execute(client::ping);
        client.pingAck.get(10, TimeUnit.SECONDS);
        assertFalse(client.frames.contains("DATA"), client.frames::toString);
    }

    /**
     * A context that delegates to the handler's context, except that it cannot create promises.
     */
    private static final class NewPromiseFailingContext implements ChannelHandlerContext {
        private final ChannelHandlerContext delegate;

        NewPromiseFailingContext(ChannelHandlerContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChannelPromise newPromise() {
            throw new IllegalStateException(PROMISE_FAILURE);
        }

        @Override
        public Channel channel() {
            return delegate.channel();
        }

        @Override
        public EventExecutor executor() {
            return delegate.executor();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public ChannelHandler handler() {
            return delegate.handler();
        }

        @Override
        public boolean isRemoved() {
            return delegate.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            delegate.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            delegate.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            delegate.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            delegate.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            delegate.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            delegate.fireUserEventTriggered(evt);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            delegate.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            delegate.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            delegate.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelHandlerContext read() {
            delegate.read();
            return this;
        }

        @Override
        public ChannelHandlerContext flush() {
            delegate.flush();
            return this;
        }

        @Override
        public ChannelPipeline pipeline() {
            return delegate.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            return delegate.alloc();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return delegate.attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            return delegate.hasAttr(key);
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return delegate.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return delegate.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return delegate.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return delegate.disconnect(promise);
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return delegate.close(promise);
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return delegate.deregister(promise);
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return delegate.write(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return delegate.writeAndFlush(msg, promise);
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return delegate.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return delegate.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return delegate.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            return delegate.voidPromise();
        }
    }

    /**
     * Sends one GET request, and records the frames of the response.
     */
    private static final class Client extends Http2ChannelDuplexHandler {
        final CompletableFuture<String> headers = new CompletableFuture<>();
        final CompletableFuture<Void> pingAck = new CompletableFuture<>();
        final List<String> frames = new CopyOnWriteArrayList<>();
        private ChannelHandlerContext context;

        @Override
        protected void handlerAdded0(ChannelHandlerContext ctx) {
            context = ctx;
        }

        void request() {
            context.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                .method("GET").scheme("http").authority("example.com").path("/full"), true).stream(newStream()));
        }

        void ping() {
            context.writeAndFlush(new DefaultHttp2PingFrame(42));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof Http2HeadersFrame h && h.headers().status() != null) {
                    frames.add("HEADERS");
                    headers.complete("status " + h.headers().status());
                } else if (msg instanceof Http2DataFrame) {
                    frames.add("DATA");
                } else if (msg instanceof Http2ResetFrame reset) {
                    frames.add("RST_STREAM " + reset.errorCode());
                } else if (msg instanceof Http2PingFrame ping && ping.ack()) {
                    pingAck.complete(null);
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
