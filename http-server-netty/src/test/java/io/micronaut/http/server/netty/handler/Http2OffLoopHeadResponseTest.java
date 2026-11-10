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
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The response to an HTTP/2 HEAD request written from a thread other than the event loop: it moves
 * to the event loop, and a failure there resets only its stream.
 */
class Http2OffLoopHeadResponseTest {

    private static final String LOOP_THREAD = "head-response-loop";

    private final EventLoopGroup serverGroup = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory(LOOP_THREAD), LocalIoHandler.newFactory());
    private final EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final CompletableFuture<OutboundAccess> access = new CompletableFuture<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final List<String> written = new CopyOnWriteArrayList<>();
    private final Client client = new Client();

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
                            outboundAccess.attachment("head");
                            access.complete(outboundAccess);
                        }

                        @Override
                        public void handleUnboundError(Throwable cause) {
                            access.completeExceptionally(cause);
                        }

                        @Override
                        public void responseFailed(Object attachment, Throwable cause) {
                            failures.add(cause);
                        }

                        @Override
                        public void responseWritten(Object attachment) {
                            written.add(String.valueOf(attachment));
                        }
                    }).build());
                }
            })
            .bind(new LocalAddress(Http2OffLoopHeadResponseTest.class))
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
        serverGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Test
    void aHeadResponseWrittenFromAnotherThreadIsSentOnTheEventLoop() throws Exception {
        OutboundAccess outboundAccess = access.get(10, TimeUnit.SECONDS);
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 42);

        // not on the event loop
        outboundAccess.writeHeadResponse(response);

        assertEquals("status 200, content-length 42, end of stream", client.outcome.get(10, TimeUnit.SECONDS));
        barrier();
        assertEquals(List.of("head"), written);
        assertTrue(failures.isEmpty(), failures::toString);
    }

    @Test
    void aHeadResponseThatFailsOnTheEventLoopResetsItsStream() throws Exception {
        OutboundAccess outboundAccess = access.get(10, TimeUnit.SECONDS);

        // the headers fail once the write reached the event loop
        outboundAccess.writeHeadResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new FailingHeaders()));

        String outcome = client.outcome.get(10, TimeUnit.SECONDS);
        assertTrue(outcome.startsWith("RST_STREAM"), outcome);
        barrier();
        assertEquals(1, failures.size(), failures::toString);
        assertEquals("cannot prepare the headers", failures.get(0).getMessage());
        assertEquals(List.of("head"), written);
    }

    private void barrier() throws Exception {
        serverGroup.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    /**
     * Headers that fail when the response is prepared on the event loop.
     */
    private static final class FailingHeaders extends DefaultHttpHeaders {
        @Override
        public HttpHeaders remove(CharSequence name) {
            if (Thread.currentThread().getName().startsWith(LOOP_THREAD)) {
                throw new IllegalStateException("cannot prepare the headers");
            }
            return super.remove(name);
        }
    }

    /**
     * Sends one HEAD request, and records how its stream ends.
     */
    private static final class Client extends Http2ChannelDuplexHandler {
        final CompletableFuture<String> outcome = new CompletableFuture<>();
        private ChannelHandlerContext context;

        @Override
        protected void handlerAdded0(ChannelHandlerContext ctx) {
            context = ctx;
        }

        void request() {
            context.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                .method("HEAD").scheme("http").authority("example.com").path("/head"), true).stream(newStream()));
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof Http2HeadersFrame headers && headers.headers().status() != null) {
                    outcome.complete("status " + headers.headers().status()
                        + ", content-length " + headers.headers().get(HttpHeaderNames.CONTENT_LENGTH)
                        + (headers.isEndStream() ? ", end of stream" : ""));
                } else if (msg instanceof Http2ResetFrame reset) {
                    outcome.complete("RST_STREAM " + reset.errorCode());
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }
}
