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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request handler that throws from {@link RequestHandler#accept} on an HTTP/2 stream: the
 * failure is answered on that stream, as on HTTP/1, and the other streams of the connection go on.
 */
class Http2AcceptFailureTest {

    private static final String ACCEPT_FAILED = "accept failed";
    private static final String UNHANDLED = ACCEPT_FAILED + ", and so does the error handler";

    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, LocalIoHandler.newFactory());
    private final CompletableFuture<OutboundAccess> held = new CompletableFuture<>();
    private final List<Throwable> unboundErrors = new CopyOnWriteArrayList<>();
    private final List<String> goAways = new CopyOnWriteArrayList<>();
    private Channel server;
    private Channel client;
    private Http2StreamChannelBootstrap streams;

    @BeforeEach
    void connect() throws Exception {
        server = new ServerBootstrap().group(group).channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
                        @Override
                        public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                            switch (request.uri()) {
                                case "/throw" -> throw new IllegalStateException(ACCEPT_FAILED);
                                case "/answer-then-throw" -> {
                                    body.close();
                                    outboundAccess.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED), NettyByteBodyFactory.empty());
                                    throw new IllegalStateException(ACCEPT_FAILED + " after the response");
                                }
                                case "/stream-then-throw" -> {
                                    body.close();
                                    // a streamed response that never completes: the failure cuts it
                                    ByteBodyFactory.StreamingBody response = new NettyByteBodyFactory(ctx.channel())
                                        .createStreamingBody(BodySizeLimits.UNLIMITED, bytesConsumed -> { });
                                    outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), response.rootBody());
                                    throw new IllegalStateException(ACCEPT_FAILED + " while streaming the response");
                                }
                                case "/abort-then-throw" -> {
                                    body.close();
                                    // the stream is reset and finished before any response
                                    outboundAccess.abort();
                                    throw new IllegalStateException(ACCEPT_FAILED + " after the abort");
                                }
                                case "/throw-unhandled" -> throw new IllegalStateException(UNHANDLED);
                                default -> {
                                    body.close();
                                    held.complete(outboundAccess);
                                }
                            }
                        }

                        @Override
                        public void handleUnboundError(Throwable cause) {
                            unboundErrors.add(cause);
                            if (UNHANDLED.equals(cause.getMessage())) {
                                throw new IllegalStateException("the error handler failed too");
                            }
                        }
                    }).build());
                }
            }).bind(new LocalAddress(Http2AcceptFailureTest.class)).sync().channel();
        client = new Bootstrap().group(group).channel(LocalChannel.class)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()),
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                if (msg instanceof Http2GoAwayFrame goAway) {
                                    goAways.add(goAway.errorCode() + " " + goAway.content().toString(StandardCharsets.UTF_8));
                                }
                                ReferenceCountUtil.release(msg);
                            }
                        });
                }
            }).connect(server.localAddress()).sync().channel();
        streams = new Http2StreamChannelBootstrap(client);
    }

    @AfterEach
    void shutdown() throws Exception {
        client.close().sync();
        server.close().sync();
        group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Test
    void aRequestWithABufferedBodyIsAnsweredWithA500() throws Exception {
        CompletableFuture<String> other = holdOtherStream();

        CompletableFuture<String> failed = new CompletableFuture<>();
        Http2StreamChannel stream = open(failed);
        stream.write(new DefaultHttp2HeadersFrame(headers("POST", "/throw"), false));
        stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("body of the failed request", StandardCharsets.UTF_8), true));

        assertEquals("status 500, end of stream", failed.get(10, TimeUnit.SECONDS));
        assertOtherStreamGoesOn(other);
    }

    @Test
    void aRequestWithAStreamedBodyIsAnsweredWithA500() throws Exception {
        CompletableFuture<String> other = holdOtherStream();

        CompletableFuture<String> failed = new CompletableFuture<>();
        Http2StreamChannel stream = open(failed);
        // the body is not complete when the headers are read: the request is accepted with a
        // streamed body
        stream.writeAndFlush(new DefaultHttp2HeadersFrame(headers("POST", "/throw"), false)).sync();
        assertEquals("status 500, end of stream", failed.get(10, TimeUnit.SECONDS));
        stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("late body", StandardCharsets.UTF_8), true));

        assertOtherStreamGoesOn(other);
    }

    @Test
    void aHandlerThatAnswersBeforeItFailsKeepsItsAnswer() throws Exception {
        CompletableFuture<String> other = holdOtherStream();

        CompletableFuture<String> answered = new CompletableFuture<>();
        open(answered).writeAndFlush(new DefaultHttp2HeadersFrame(headers("GET", "/answer-then-throw"), true));

        assertEquals("status 202, end of stream", answered.get(10, TimeUnit.SECONDS));
        assertOtherStreamGoesOn(other);
    }

    @Test
    void aHandlerThatFailsWhileItStreamsItsAnswerResetsOnlyItsStream() throws Exception {
        CompletableFuture<String> other = holdOtherStream();

        CompletableFuture<String> cut = new CompletableFuture<>();
        open(cut).writeAndFlush(new DefaultHttp2HeadersFrame(headers("GET", "/stream-then-throw"), true));

        String outcome = cut.get(10, TimeUnit.SECONDS);
        assertTrue(outcome.endsWith("RST_STREAM"), outcome);
        assertOtherStreamGoesOn(other);
    }

    @Test
    void aHandlerThatAbortsBeforeItFailsResetsOnlyItsStream() throws Exception {
        CompletableFuture<String> other = holdOtherStream();

        CompletableFuture<String> aborted = new CompletableFuture<>();
        open(aborted).writeAndFlush(new DefaultHttp2HeadersFrame(headers("GET", "/abort-then-throw"), true));

        String outcome = aborted.get(10, TimeUnit.SECONDS);
        assertTrue(outcome.startsWith("RST_STREAM"), outcome);
        assertOtherStreamGoesOn(other);
    }

    @Test
    void aFailingErrorHandlerDoesNotStopTheAnswer() throws Exception {
        CompletableFuture<String> other = holdOtherStream();

        CompletableFuture<String> failed = new CompletableFuture<>();
        open(failed).writeAndFlush(new DefaultHttp2HeadersFrame(headers("GET", "/throw-unhandled"), true));

        assertEquals("status 500, end of stream", failed.get(10, TimeUnit.SECONDS));
        assertOtherStreamGoesOn(other);
    }

    /**
     * The request body is released when the handler fails without claiming it: the DATA frames
     * hold slices of the read buffer, so its reference count shows whether they were released.
     */
    @Test
    void theBodyOfARequestThatIsNotAcceptedIsReleased() {
        assertBodyReleased(true);
    }

    @Test
    void theStreamedBodyOfARequestThatIsNotAcceptedIsReleased() {
        assertBodyReleased(false);
    }

    private void assertBodyReleased(boolean complete) {
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        EmbeddedChannel embeddedServer = new EmbeddedChannel(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                throw new IllegalStateException(ACCEPT_FAILED);
            }

            @Override
            public void handleUnboundError(Throwable cause) {
                errors.add(cause);
            }
        }).build());
        Http2ChannelDuplexHandler duplexHandler = new Http2ChannelDuplexHandler() { };
        EmbeddedChannel embeddedClient = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler);
        try {
            Http2FrameStream stream = duplexHandler.newStream();
            embeddedClient.writeOutbound(new DefaultHttp2HeadersFrame(headers("POST", "/throw"), false).stream(stream));
            embeddedClient.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8), complete).stream(stream));

            // a single contiguous buffer becomes the decoder's cumulation as is, so the DATA
            // frames are slices of it
            ByteBuf read = Unpooled.buffer();
            ByteBuf msg;
            while ((msg = embeddedClient.readOutbound()) != null) {
                read.writeBytes(msg);
                msg.release();
            }
            embeddedServer.writeOneInbound(read);
            embeddedServer.pipeline().fireChannelReadComplete();
            embeddedServer.runPendingTasks();
            embeddedServer.checkException();

            assertEquals(1, errors.size(), errors::toString);
            assertEquals(0, read.refCnt());
            assertTrue(embeddedServer.isActive());
        } finally {
            embeddedServer.finishAndReleaseAll();
            embeddedClient.finishAndReleaseAll();
        }
    }

    private CompletableFuture<String> holdOtherStream() throws Exception {
        CompletableFuture<String> other = new CompletableFuture<>();
        open(other).writeAndFlush(new DefaultHttp2HeadersFrame(headers("GET", "/hold"), true));
        held.get(10, TimeUnit.SECONDS);
        return other;
    }

    private void assertOtherStreamGoesOn(CompletableFuture<String> other) throws Exception {
        held.getNow(null).write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty());
        assertEquals("status 200, end of stream", other.get(10, TimeUnit.SECONDS));
        // the handler's own failure reaches it as an unbound error
        server.eventLoop().submit(() -> { }).get(10, TimeUnit.SECONDS);
        assertEquals(1, unboundErrors.size(), unboundErrors::toString);
        assertTrue(unboundErrors.get(0).getMessage().startsWith(ACCEPT_FAILED), unboundErrors::toString);
        assertEquals(List.of(), goAways);
        assertTrue(client.isActive());
    }

    /**
     * Open a stream whose outcome is its status, then how it ended: the end of the stream, or a
     * reset. A response that neither ends nor is reset never completes the outcome.
     */
    private Http2StreamChannel open(CompletableFuture<String> result) throws Exception {
        return streams.handler(new ChannelInboundHandlerAdapter() {
            private String status = "";

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof Http2HeadersFrame headers) {
                    if (headers.headers().status() != null) {
                        status = "status " + headers.headers().status() + ", ";
                    }
                    if (headers.isEndStream()) {
                        result.complete(status + "end of stream");
                    }
                } else if (msg instanceof Http2DataFrame data && data.isEndStream()) {
                    result.complete(status + "end of stream");
                }
                ReferenceCountUtil.release(msg);
            }

            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                // the multiplex handler delivers a reset of the stream as a user event
                if (evt instanceof Http2ResetFrame) {
                    result.complete(status + "RST_STREAM");
                }
                ctx.fireUserEventTriggered(evt);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                result.complete(status + "closed");
            }
        }).open().sync().getNow();
    }

    private static DefaultHttp2Headers headers(String method, String path) {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.method(method).path(path).scheme("http").authority("localhost");
        return headers;
    }
}
