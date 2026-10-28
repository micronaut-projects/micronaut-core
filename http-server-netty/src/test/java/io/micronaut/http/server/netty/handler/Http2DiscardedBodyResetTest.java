package io.micronaut.http.server.netty.handler;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client that keeps uploading the body of a request the server answered without reading it
 * must be told to stop: the stream is reset once the response is written, whatever the order in
 * which the body was discarded and the response was written. Otherwise the body, which nothing
 * consumes, exhausts the flow control window of the connection, and the other requests of the
 * connection cannot send a body anymore.
 */
class Http2DiscardedBodyResetTest {
    private static final String SPEC = "Http2DiscardedBodyResetTest";
    private static final int CHUNK_SIZE = 16384;
    private static final ByteBuf CHUNK = Unpooled.unreleasableBuffer(Unpooled.wrappedBuffer(new byte[CHUNK_SIZE]));
    /**
     * Far more than any flow control window the server grants: the upload cannot end by itself.
     */
    private static final long ENDLESS = 1L << 40;
    private static final int PROBE_SIZE = 1000;

    private static ApplicationContext context;
    private static EmbeddedServer server;
    private static NioEventLoopGroup group;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of(
            "spec.name", SPEC,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0"
        ));
        server = context.getBean(EmbeddedServer.class);
        server.start();
        group = new NioEventLoopGroup(1);
    }

    @AfterAll
    static void stop() throws InterruptedException {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        context.close();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "a blocking route without a body argument, /discarded/blocking, 200",
        "a route without a body argument, /discarded/plain, 200",
        "no route, /discarded/missing, 404",
        "a filter that rejects the request, /discarded/secured, 401",
    })
    @Timeout(30)
    void theStreamOfAnUnreadBodyIsResetAndTheConnectionStaysUsable(String name, String path, String status) throws Exception {
        try (RawClient client = new RawClient(group, server.getPort())) {
            // no Content-Length: the server cannot tell how much of the body is left
            StreamState upload = client.post(path, MediaType.APPLICATION_OCTET_STREAM, ENDLESS, false);
            assertTrue(upload.responseEnd.await(10, TimeUnit.SECONDS), "no response to the upload");
            assertEquals(status, upload.status);
            assertTrue(upload.reset.await(5, TimeUnit.SECONDS), "the stream was not reset: the client keeps uploading");
            assertEquals(Http2Error.CANCEL.code(), upload.resetCode);

            // more than the window of the connection was offered, so without the reset nothing
            // of this body would get through
            StreamState probe = client.post("/discarded/echo", MediaType.TEXT_PLAIN, PROBE_SIZE, true);
            assertTrue(probe.responseEnd.await(5, TimeUnit.SECONDS), "a request with a body on the same connection did not complete");
            assertEquals("200", probe.status);
            assertEquals("len=" + PROBE_SIZE, probe.body.toString());
            assertNotNull(client.channel);
            assertTrue(client.channel.isActive(), "the connection was closed");
        }
    }

    private static final class StreamState {
        final int id;
        final CountDownLatch responseEnd = new CountDownLatch(1);
        final CountDownLatch reset = new CountDownLatch(1);
        final AtomicLong written = new AtomicLong();
        final StringBuilder body = new StringBuilder();
        volatile String status;
        volatile long resetCode = -1;

        StreamState(int id) {
            this.id = id;
        }
    }

    /**
     * An HTTP/2 client with prior knowledge that does not stop sending the body of a request
     * when the response arrives, only when the stream is reset or its window is exhausted.
     */
    private static final class RawClient implements AutoCloseable {
        final Channel channel;
        final Http2ConnectionHandler handler;
        final Http2Connection connection = new DefaultHttp2Connection(false);
        final Map<Integer, StreamState> streams = new ConcurrentHashMap<>();

        RawClient(NioEventLoopGroup group, int port) throws InterruptedException {
            handler = new Http2ConnectionHandlerBuilder()
                // close at once: a stream that was not reset would hold the close back
                .gracefulShutdownTimeoutMillis(0)
                .connection(connection).frameListener(new Http2FrameAdapter() {
                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding, boolean endStream) {
                    StreamState s = streams.get(streamId);
                    if (s != null) {
                        if (headers.status() != null) {
                            s.status = headers.status().toString();
                        }
                        if (endStream) {
                            s.responseEnd.countDown();
                        }
                    }
                }

                @Override
                public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
                    onHeadersRead(ctx, streamId, headers, padding, endStream);
                }

                @Override
                public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) {
                    StreamState s = streams.get(streamId);
                    if (s != null) {
                        s.body.append(data.toString(StandardCharsets.UTF_8));
                        if (endOfStream) {
                            s.responseEnd.countDown();
                        }
                    }
                    return data.readableBytes() + padding;
                }

                @Override
                public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
                    StreamState s = streams.get(streamId);
                    if (s != null) {
                        s.resetCode = errorCode;
                        s.reset.countDown();
                    }
                }
            }).build();
            channel = new Bootstrap().group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(handler);
                }
            }).connect("127.0.0.1", port).sync().channel();
        }

        StreamState post(String path, String contentType, long bodySize, boolean contentLength) throws Exception {
            return channel.eventLoop().submit(() -> {
                ChannelHandlerContext ctx = channel.pipeline().context(handler);
                StreamState s = new StreamState(connection.local().incrementAndGetNextStreamId());
                streams.put(s.id, s);
                Http2Headers headers = new DefaultHttp2Headers().method("POST").scheme("http").authority("localhost").path(path);
                headers.set("content-type", contentType);
                if (contentLength) {
                    headers.setLong("content-length", bodySize);
                }
                handler.encoder().writeHeaders(ctx, s.id, headers, 0, false, ctx.newPromise());
                pump(ctx, s, bodySize);
                return s;
            }).get();
        }

        private void pump(ChannelHandlerContext ctx, StreamState s, long total) {
            long remaining = total - s.written.get();
            int n = (int) Math.min(CHUNK_SIZE, remaining);
            ChannelPromise promise = ctx.newPromise();
            promise.addListener(future -> {
                // a failure is the reset of the stream: the upload stops
                if (future.isSuccess() && s.written.addAndGet(n) < total) {
                    pump(ctx, s, total);
                }
            });
            handler.encoder().writeData(ctx, s.id, CHUNK.retainedSlice(0, n), 0, n == remaining, promise);
            handler.flush(ctx);
        }

        @Override
        public void close() {
            channel.close().syncUninterruptibly();
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/discarded")
    static class DiscardedController {
        @Post("/blocking")
        @Consumes(MediaType.ALL)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String blocking() {
            return "ok";
        }

        @Post("/plain")
        @Consumes(MediaType.ALL)
        String plain() {
            return "ok";
        }

        @Post("/secured")
        @Consumes(MediaType.ALL)
        String secured() {
            return "secret";
        }

        @Post("/echo")
        @Consumes(MediaType.ALL)
        String echo(@Body byte[] body) {
            return "len=" + body.length;
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @ServerFilter("/discarded/secured")
    static class DenyFilter {
        @RequestFilter
        HttpResponse<?> deny(HttpRequest<?> request) {
            return HttpResponse.unauthorized();
        }
    }
}
