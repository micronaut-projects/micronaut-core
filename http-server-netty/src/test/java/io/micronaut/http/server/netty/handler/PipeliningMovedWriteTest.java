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

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.ByteBody.SplitBackpressureMode;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
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
import java.util.concurrent.CountDownLatch;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An HTTP/1 response written from a thread other than the event loop moves to the event loop: a
 * streamed body is claimed on the calling thread and its response is prepared there, and a full
 * response is handed over once prepared. The event loop may reject the hop, and a failure after it
 * has no caller left to answer it.
 */
class PipeliningMovedWriteTest {

    private static final String REQUEST = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
    private static final String GZIP_REQUEST = "GET / HTTP/1.1\r\nHost: example.com\r\nAccept-Encoding: gzip\r\n\r\n";
    private static final String ZSTD_REQUEST ="GET / HTTP/1.1\r\nHost: example.com\r\nAccept-Encoding: zstd\r\n\r\n";
    private static final String CLOSE_GZIP_REQUEST = "GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\nAccept-Encoding: gzip\r\n\r\n";
    private static final int COMPRESSED_BODY_SIZE = 4096;
    private static final int HELD_PIECE_SIZE = PieceAccumulator.SMALL_PIECE_LIMIT / 2;

    private final RejectingEventLoop serverLoop = new RejectingEventLoop();
    private final EventLoopGroup clientGroup = new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory());
    private final BlockingQueue<OutboundAccess> accesses = new LinkedBlockingQueue<>();
    private final CompletableFuture<Channel> connection = new CompletableFuture<>();
    private final List<String> callbacks = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final CompletableFuture<String> received = new CompletableFuture<>();
    /**
     * A handler next to the socket of the server connection, or {@code null}.
     */
    @Nullable
    private ChannelHandler socketHandler;
    private final CompletableFuture<PipeliningServerHandler> serverHandler = new CompletableFuture<>();

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
    void aCompressionStrategySeesTheConnectionHeaderOfAMovedFullWrite() throws Exception {
        assertNotCompressedWhenTheConnectionCloses(false);
    }

    @Test
    void aCompressionStrategySeesTheConnectionHeaderOfAFullWriteOnTheEventLoop() throws Exception {
        assertNotCompressedWhenTheConnectionCloses(true);
    }

    @Test
    void aMovedFullWriteWhoseConnectionHeaderFailsThrowsToTheCaller() throws Exception {
        OutboundAccess outboundAccess = connect(1, CLOSE_GZIP_REQUEST, null);
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBuf content = Unpooled.copiedBuffer("hello", StandardCharsets.US_ASCII);

        // not on the event loop: the connection header of the request is added here, and fails
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new ConnectionRejectingHeaders()), factory.adapt(content)));
        assertEquals("cannot add the connection header", failure.getMessage());
        assertEquals(0, content.refCnt());
    }

    @Test
    void aFullWriteWhoseCompressionStrategyFailsOffTheEventLoopThrowsToTheCaller() throws Exception {
        OutboundAccess outboundAccess = connect(1, GZIP_REQUEST, throwingStrategyCompressor());
        outboundAccess.attachment("request");
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBuf content = Unpooled.copiedBuffer("x".repeat(COMPRESSED_BODY_SIZE), StandardCharsets.US_ASCII);

        // not on the event loop: the compression is set up here, and its failure reaches the caller
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(content)));
        assertEquals("cannot decide the compression", failure.getMessage());
        assertEquals(0, content.refCnt());

        // the caller answers the request with its error response instead
        outboundAccess.closeAfterWrite();
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR), NettyByteBodyFactory.empty());
        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertCallbacks("written request");
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

    @Test
    void aLaterResponseThatFailsToStartAfterAMovedStreamedWriteClosesTheConnection() throws Exception {
        OutboundAccess first = connect(2);
        first.attachment("first");
        queueFailingSecondResponse();
        ByteBodyFactory.StreamingBody body = streamingBody(null);
        serverLoop.submit(() -> body.sharedBuffer().complete()).get(10, TimeUnit.SECONDS);

        // the moved write completes the first response and opens the second one, whose body fails
        first.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody());

        // the second response may be cut: the connection closes, which discards it
        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        assertCallbacks("written first", "written second");
    }

    @Test
    void aLaterResponseThatFailsToStartAfterAMovedFullWriteClosesTheConnection() throws Exception {
        OutboundAccess first = connect(2);
        first.attachment("first");
        queueFailingSecondResponse();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));

        // the full response is handed over to the event loop, where its write opens the second response
        first.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(Unpooled.copiedBuffer("first", StandardCharsets.US_ASCII)));

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        assertCallbacks("written first", "written second");
    }

    @Test
    void aMovedFullWriteWhoseCompressionFailsClosesTheConnection() throws Exception {
        assumeTrue(Zstd.isAvailable(), "the encode limit needs zstd");
        OutboundAccess outboundAccess = connect(1, ZSTD_REQUEST, failingCompressor());
        outboundAccess.attachment("request");
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));

        // the response is compressed on the event loop, after the hop, and exceeds the encode limit
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(Unpooled.wrappedBuffer(new byte[COMPRESSED_BODY_SIZE])));

        received.get(10, TimeUnit.SECONDS);
        assertCallbacks("failed request", "written request");
    }

    @Test
    void aMovedStreamedWriteWhoseFinalCompressionFailsIsDiscarded() throws Exception {
        assumeTrue(Zstd.isAvailable(), "the encode limit needs zstd");
        OutboundAccess outboundAccess = connect(1, ZSTD_REQUEST, failingCompressor());
        outboundAccess.attachment("request");
        ByteBodyFactory.StreamingBody body = streamingBody(null);
        Channel channel = connection.get(10, TimeUnit.SECONDS);
        // a small piece is held and sent with the end of the body, in the final write
        serverLoop.submit(() -> body.sharedBuffer().addAndComplete(NettyReadBufferFactory.of(channel.alloc()).adapt(Unpooled.wrappedBuffer(new byte[HELD_PIECE_SIZE])))).get(10, TimeUnit.SECONDS);

        // the final write fails to compress after the handler detached itself for it
        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody());

        received.get(10, TimeUnit.SECONDS);
        assertCallbacks("failed request", "written request");
    }

    @Test
    void aSecondMovedFullWriteForTheSameRequestIsDroppedAndReleased() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBuf second = Unpooled.copiedBuffer("second", StandardCharsets.US_ASCII);

        // both writes are handed over before the event loop installs the first one
        holdEventLoop(() -> {
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(Unpooled.copiedBuffer("first", StandardCharsets.US_ASCII)));
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(second));
        });

        barrier();
        assertEquals(0, second.refCnt());
        assertCallbacks("written request");
    }

    @Test
    void aSecondMovedStreamedWriteForTheSameRequestIsDroppedAndClosed() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        ByteBodyFactory.StreamingBody first = streamingBody(null);
        serverLoop.submit(() -> first.sharedBuffer().complete()).get(10, TimeUnit.SECONDS);
        CompletableFuture<Void> secondDiscarded = new CompletableFuture<>();
        ByteBodyFactory.StreamingBody second = streamingBody(secondDiscarded::complete);

        holdEventLoop(() -> {
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), first.rootBody());
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), second.rootBody());
        });

        secondDiscarded.get(10, TimeUnit.SECONDS);
        assertCallbacks("written request");
    }

    @Test
    void aDroppedMovedStreamedWriteWhoseCleanupFailsIsStillDropped() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        ByteBodyFactory.StreamingBody first = streamingBody(null);
        serverLoop.submit(() -> first.sharedBuffer().complete()).get(10, TimeUnit.SECONDS);
        FailingCloseBody second = new FailingCloseBody();

        holdEventLoop(() -> {
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), first.rootBody());
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), second);
        });

        barrier();
        assertEquals(1, second.closes);
        assertCallbacks("written request");
        assertTrue(failures.isEmpty(), failures::toString);
    }

    @Test
    void aMovedStreamedWriteWhoseBodyFailsAfterItsCompressionWasSetUpIsAnsweredWithA500() throws Exception {
        OutboundAccess outboundAccess = connect(1, GZIP_REQUEST, gzipCompressor());
        outboundAccess.attachment("request");
        // the body cannot be read: it fails once the compression session was created for it
        FailingCloseBody body = new FailingCloseBody();

        outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body);

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertCallbacks("failed request", "written request");
        assertInstanceOf(UnsupportedOperationException.class, failures.get(0));
    }

    @Test
    void aGracefulShutdownBeforeAMovedFullWriteIsInstalledAddsConnectionClose() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        PipeliningServerHandler handler = serverHandler.get(10, TimeUnit.SECONDS);
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));

        // the shutdown reaches the event loop after the response was prepared on this thread, but
        // before the event loop installs it
        holdEventLoop(() -> {
            handler.shutdownGracefully();
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(Unpooled.copiedBuffer("hello", StandardCharsets.US_ASCII)));
        });

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        assertTrue(response.toLowerCase().contains("connection: close"), response);
        assertCallbacks("written request");
    }

    @Test
    void aMovedFullWriteWhoseCloseHeaderFailsIsReleasedAndAnsweredWithA500() throws Exception {
        OutboundAccess outboundAccess = connect(1);
        outboundAccess.attachment("request");
        PipeliningServerHandler handler = serverHandler.get(10, TimeUnit.SECONDS);
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        ByteBuf content = Unpooled.copiedBuffer("hello", StandardCharsets.US_ASCII);

        // the close marked by the shutdown is added on the event loop, where these headers fail
        holdEventLoop(() -> {
            handler.shutdownGracefully();
            outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new FailingConnectionHeaders()), factory.adapt(content));
        });

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertCallbacks("failed request", "written request");
        assertEquals(0, content.refCnt());
    }

    /**
     * Run the writes while the event loop is busy, so that each of them is handed over before
     * the event loop runs any.
     */
    private void holdEventLoop(ThrowingRunnable writes) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch held = new CountDownLatch(1);
        serverLoop.execute(() -> {
            held.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(held.await(10, TimeUnit.SECONDS));
        try {
            writes.run();
        } finally {
            release.countDown();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Compressor gzipCompressor() {
        return Compressor.create(new HttpCompressionStrategy() {
            @Override
            public boolean shouldCompress(io.netty.handler.codec.http.HttpResponse response) {
                return true;
            }

            @Override
            public int getMaxZstdEncodeSize() {
                return Integer.MAX_VALUE;
            }
        });
    }

    @Test
    void theResponseBeforeALaterResponseThatFailsToStartIsSentOverASlowSocket() throws Exception {
        // the socket takes flushed data late, as one whose send buffer is full does
        socketHandler = new LateFlushHandler();
        OutboundAccess first = connect(2);
        first.attachment("first");
        queueFailingSecondResponse();
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));

        first.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), factory.adapt(Unpooled.copiedBuffer("first", StandardCharsets.US_ASCII)));

        // the connection closes only once the first response is out
        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        assertTrue(response.contains("\r\n\r\nfirst"), response);
        // the second response is discarded when the connection closes, which happens after the
        // late flush of the first one: wait for it rather than for one turn of the event loop
        awaitCallbacks("written first", "written second");
    }

    /**
     * Queue the second response of the connection, whose body fails when it starts, i.e. once
     * the response is opened.
     */
    private void queueFailingSecondResponse() throws Exception {
        OutboundAccess second = accesses.poll(10, TimeUnit.SECONDS);
        assertNotNull(second);
        second.attachment("second");
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
        second.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body.rootBody());
        barrier();
    }

    /**
     * A compressor whose zstd encode limit is smaller than the compressed body.
     */
    private static Compressor failingCompressor() {
        return Compressor.create(new HttpCompressionStrategy() {
            @Override
            public boolean shouldCompress(io.netty.handler.codec.http.HttpResponse response) {
                return true;
            }

            @Override
            public int getMaxZstdEncodeSize() {
                return 64;
            }
        });
    }

    /**
     * Write a compressible response to a request that closes the connection, with a strategy
     * that does not compress a response that closes the connection.
     *
     * @param onEventLoop Whether to write it on the event loop
     */
    private void assertNotCompressedWhenTheConnectionCloses(boolean onEventLoop) throws Exception {
        Compressor compressor = Compressor.create(new HttpCompressionStrategy() {
            @Override
            public boolean shouldCompress(io.netty.handler.codec.http.HttpResponse response) {
                return !response.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true);
            }

            @Override
            public int getMaxZstdEncodeSize() {
                return Integer.MAX_VALUE;
            }
        });
        OutboundAccess outboundAccess = connect(1, CLOSE_GZIP_REQUEST, compressor);
        NettyByteBodyFactory factory = new NettyByteBodyFactory(connection.get(10, TimeUnit.SECONDS));
        String body = "x".repeat(COMPRESSED_BODY_SIZE);
        ThrowingRunnable write = () -> outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
            factory.adapt(Unpooled.copiedBuffer(body, StandardCharsets.US_ASCII)));
        if (onEventLoop) {
            serverLoop.submit(() -> {
                write.run();
                return null;
            }).get(10, TimeUnit.SECONDS);
        } else {
            write.run();
        }

        String response = received.get(10, TimeUnit.SECONDS);
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        assertTrue(response.toLowerCase().contains("connection: close"), response);
        assertTrue(!response.toLowerCase().contains("content-encoding"), response);
        assertTrue(response.endsWith(body), response);
    }

    /**
     * Headers that fail to add a {@code connection} header.
     */
    private static final class ConnectionRejectingHeaders extends DefaultHttpHeaders {
        @Override
        public HttpHeaders add(CharSequence name, Object value) {
            if (HttpHeaderNames.CONNECTION.contentEqualsIgnoreCase(name)) {
                throw new IllegalStateException("cannot add the connection header");
            }
            return super.add(name, value);
        }
    }

    /**
     * A compressor whose strategy fails to decide whether to compress a response.
     */
    private static Compressor throwingStrategyCompressor() {
        return Compressor.create(new HttpCompressionStrategy() {
            @Override
            public boolean shouldCompress(io.netty.handler.codec.http.HttpResponse response) {
                throw new IllegalStateException("cannot decide the compression");
            }

            @Override
            public int getMaxZstdEncodeSize() {
                return Integer.MAX_VALUE;
            }
        });
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

    /**
     * The callbacks of the request handler, once they match or 10 seconds have passed: for
     * callbacks made by work the event loop scheduled for later.
     */
    private void awaitCallbacks(String... expected) throws Exception {
        List<String> expectedList = List.of(expected);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!expectedList.equals(callbacks) && System.nanoTime() < deadline) {
            barrier();
            Thread.sleep(10);
        }
        assertEquals(expectedList, callbacks);
    }

    private void barrier() throws Exception {
        serverLoop.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private OutboundAccess connect(int requests) throws Exception {
        return connect(requests, REQUEST, null);
    }

    private OutboundAccess connect(int requests, String request, @Nullable Compressor compressor) throws Exception {
        Channel serverChannel = new ServerBootstrap()
            .group(serverLoop)
            .channel(LocalServerChannel.class)
            .childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    PipeliningServerHandler handler = new PipeliningServerHandler(new RequestHandler() {
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
                    });
                    handler.setCompressor(compressor);
                    serverHandler.complete(handler);
                    if (socketHandler != null) {
                        ch.pipeline().addLast(socketHandler);
                    }
                    ch.pipeline().addLast(new HttpServerCodec(), handler);
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
        clientChannel.writeAndFlush(Unpooled.copiedBuffer(request.repeat(requests), StandardCharsets.US_ASCII));
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
     * Headers that fail when the {@code connection} header is set on the event loop.
     */
    private static final class FailingConnectionHeaders extends DefaultHttpHeaders {
        @Override
        public HttpHeaders set(CharSequence name, Object value) {
            if (HttpHeaderNames.CONNECTION.contentEqualsIgnoreCase(name) && Thread.currentThread().getName().startsWith("rejecting-loop")) {
                throw new IllegalStateException("cannot set the connection header");
            }
            return super.set(name, value);
        }
    }

    /**
     * Passes each flush on to the socket a little later: a close in the meantime drops the data
     * that was written but not flushed yet.
     */
    private static final class LateFlushHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void flush(ChannelHandlerContext ctx) {
            Runnable flush = ctx::flush;
            ctx.executor().schedule(flush, 50, TimeUnit.MILLISECONDS);
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
