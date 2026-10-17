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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-request callbacks of {@link ServerHttpRequest#whenResponseCompleted} and
 * {@link ServerHttpRequest#whenAbandoned}, for handler routes and controllers, over HTTP/1.1 and
 * HTTP/2.
 */
class ResponseCallbacksTest {
    private static final String SPEC_NAME = "ResponseCallbacksTest";
    private static final long TIMEOUT_MILLIS = 10_000;
    private static final int LARGE_BODY = 32 * 1024 * 1024;

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static ApplicationContext h2Ctx;
    private static EmbeddedServer h2Server;
    private static EventLoopGroup group;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
        h2Ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false
        ));
        h2Server = h2Ctx.getBean(EmbeddedServer.class).start();
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    }

    @AfterAll
    static void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
        if (ctx != null) {
            ctx.close();
        }
        if (h2Ctx != null) {
            h2Ctx.close();
        }
    }

    @Test
    void handlerRouteCompletedWithoutFailure() throws Exception {
        assertEquals(200, get("/handler/ok?id=handler-ok"));
        assertNull(failure(ctx, "handler-ok"));
    }

    @Test
    void controllerCompletedWithoutFailure() throws Exception {
        assertEquals(200, get("/callbacks/ok?id=controller-ok"));
        assertNull(failure(ctx, "controller-ok"));
    }

    @Test
    void closingTheConnectionAfterTheResponseIsNotAnAbandonment() throws Exception {
        rawExchange("GET /handler/ok?id=close HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertNull(failure(ctx, "close"));
        assertFalse(ctx.getBean(Recorder.class).abandoned("close").isDone());
    }

    @Test
    void anHttp10ResponseIsNotAnAbandonment() throws Exception {
        rawExchange("GET /handler/ok?id=http10 HTTP/1.0\r\n\r\n");
        assertNull(failure(ctx, "http10"));
        assertFalse(ctx.getBean(Recorder.class).abandoned("http10").isDone());
    }

    @Test
    void routeErrorIsReported() throws Exception {
        assertEquals(500, get("/handler/error?id=handler-error"));
        assertEquals("route failed", failure(ctx, "handler-error").getMessage());
    }

    @Test
    void handledErrorIsNotReported() throws Exception {
        assertEquals(400, get("/callbacks/handled?id=controller-handled"));
        assertNull(failure(ctx, "controller-handled"));
    }

    @Test
    void aFailureBeforeTheFirstRegistrationIsReported() throws Exception {
        // the response filter registers after the default error response recorded the failure
        assertEquals(500, get("/callbacks/late-error?id=late-error"));
        assertEquals("late route failed", failure(ctx, "late-error").getMessage());
    }

    @Test
    void aRequestRewrittenByAFilterKeepsTheCallbacks() throws Exception {
        assertEquals(200, get("/callbacks/rewritten?id=rewritten"));
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals("true", recorder.requests.get("rewritten").getParameters().get("rewritten"));
        assertNull(failure(ctx, "rewritten"));
    }

    @Test
    void anUnhandledErrorOfARewrittenRequestIsReported() throws Exception {
        assertEquals(500, get("/callbacks/rewritten-error?id=rewritten-error"));
        assertEquals("rewritten route failed", failure(ctx, "rewritten-error").getMessage());
    }

    @Test
    void theCompletionWaitsForTheFinalWriteAndReportsItsFailure() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(4096);
            socket.connect(new InetSocketAddress(server.getHost(), server.getPort()));
            OutputStream out = socket.getOutputStream();
            out.write("GET /handler/large?id=large HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            // the head of the response arrives, the rest stays queued while the client does not read
            assertTrue(socket.getInputStream().read(new byte[16]) > 0);
            Thread.sleep(500);
            assertFalse(recorder.completion("large").isDone(), "the response is still being written");
        }
        assertNotNull(failure(ctx, "large"), "the final write failed when the client went away");
    }

    @Test
    void anAbandonmentWhileTheFinalWriteIsQueuedIsReportedOverHttp1() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        for (String path : new String[]{"/handler/large?id=h1-queued", "/handler/large-abandon-only?id=h1-queued-only"}) {
            String id = path.substring(path.indexOf("id=") + 3);
            try (Socket socket = new Socket()) {
                socket.setReceiveBufferSize(4096);
                socket.connect(new InetSocketAddress(server.getHost(), server.getPort()));
                OutputStream out = socket.getOutputStream();
                out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                assertTrue(socket.getInputStream().read(new byte[16]) > 0);
                Thread.sleep(200);
                assertFalse(recorder.abandoned(id).isDone());
            }
            recorder.abandoned(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
        assertNotNull(failure(ctx, "h1-queued"));
    }

    @Test
    void anAbandonmentWhileTheFinalWriteIsFlowControlledIsReportedOverHttp2() throws Exception {
        Recorder recorder = h2Ctx.getBean(Recorder.class);
        for (String id : new String[]{"h2-queued", "h2-queued-only"}) {
            String path = id.endsWith("only") ? "/handler/large-abandon-only?id=" + id : "/handler/large?id=" + id;
            H2Client client = new H2Client();
            Channel channel = connectHttp2(client);
            try {
                // the client never returns the flow control window: the body waits on the server
                Http2FrameStream stream = request(channel, client, path);
                client.firstData.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                Thread.sleep(200);
                assertFalse(recorder.abandoned(id).isDone());
                channel.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream)).sync();
                recorder.abandoned(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } finally {
                channel.close().sync();
            }
        }
        assertNotNull(failure(h2Ctx, "h2-queued"));
    }

    @Test
    void aBodylessHttp2ResponseWhoseHeadersCannotBeConvertedIsReported() throws Exception {
        H2Client client = new H2Client();
        Channel channel = connectHttp2(client);
        try {
            request(channel, client, "/handler/bad-te?id=h2-bad-te");
            assertNotNull(failure(h2Ctx, "h2-bad-te"));
        } finally {
            channel.close().sync();
        }
    }

    @Test
    void aFailureOfAReplacedRequestIsReportedToTheOriginal() throws Exception {
        assertEquals(500, get("/callbacks/replaced-error?id=replaced-error"));
        assertEquals("replaced route failed", failure(ctx, "replaced-error").getMessage());
    }

    @Test
    void aHandledWriterErrorIsNotReported() throws Exception {
        assertEquals(409, get("/callbacks/writer-handled?id=writer-handled"));
        assertNull(failure(ctx, "writer-handled"));
    }

    @Test
    void anUnhandledWriterErrorIsReported() throws Exception {
        assertEquals(500, get("/callbacks/writer-unhandled?id=writer-unhandled"));
        assertEquals("unhandled writer failure", failure(ctx, "writer-unhandled").getMessage());
    }

    @Test
    void bodyFailureAfterTheResponseStartedIsReportedOverHttp1() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write("GET /callbacks/stream?id=h1-stream HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String received = readUntil(socket.getInputStream(), "first");
            assertTrue(received.startsWith("HTTP/1.1 200"), received);
            assertFalse(recorder.completion("h1-stream").isDone());
            recorder.gate("h1-stream").complete(null);
            assertEquals("body failed", failure(ctx, "h1-stream").getMessage());
        }
    }

    @Test
    void bodyFailureAfterTheResponseStartedIsReportedOverHttp2() throws Exception {
        Recorder recorder = h2Ctx.getBean(Recorder.class);
        H2Client client = new H2Client();
        Channel channel = connectHttp2(client);
        try {
            request(channel, client, "/callbacks/stream?id=h2-stream");
            client.firstData.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertFalse(recorder.completion("h2-stream").isDone());
            recorder.gate("h2-stream").complete(null);
            assertEquals("body failed", failure(h2Ctx, "h2-stream").getMessage());
            client.reset.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            channel.close().sync();
        }
    }

    @Test
    void registeringAfterTheResponseIsDoneIsRefused() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals(200, get("/handler/ok?id=late"));
        recorder.completion("late").get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        ServerHttpRequest<?> request = recorder.requests.get("late");
        assertFalse(request.whenResponseCompleted(t -> { }));
    }

    @Test
    void aFirstRegistrationAfterTheResponseIsDoneIsRefusedOrRuns() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals(200, get("/handler/unregistered?id=unregistered"));
        ServerHttpRequest<?> request = recorder.requests.get("unregistered");
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        // the request is cleaned up right after the response, which the client may see first: a
        // registration before that runs, one after it is refused
        while (true) {
            CompletableFuture<Optional<Throwable>> ran = new CompletableFuture<>();
            if (!request.whenResponseCompleted(t -> ran.complete(Optional.ofNullable(t)))) {
                break;
            }
            assertNull(ran.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElse(null));
            assertTrue(System.currentTimeMillis() < deadline, "never refused");
            Thread.sleep(20);
        }
    }

    @Test
    void handlerRouteAbandonedWhenTheHttp1ConnectionCloses() throws Exception {
        abandonOverHttp1("/handler/pending?id=h1-handler", "h1-handler");
    }

    @Test
    void controllerAbandonedWhenTheHttp1ConnectionCloses() throws Exception {
        abandonOverHttp1("/callbacks/pending?id=h1-controller", "h1-controller");
    }

    @Test
    void handlerRouteAbandonedWhenTheHttp2StreamIsReset() throws Exception {
        abandonOverHttp2("/handler/pending?id=h2-handler", "h2-handler");
    }

    @Test
    void controllerAbandonedWhenTheHttp2StreamIsReset() throws Exception {
        abandonOverHttp2("/callbacks/pending?id=h2-controller", "h2-controller");
    }

    private static void abandonOverHttp1(String path, String id) throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            recorder.pending(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertFalse(recorder.abandoned(id).isDone());
        }
        assertAbandoned(recorder, id);
    }

    private static void abandonOverHttp2(String path, String id) throws Exception {
        Recorder recorder = h2Ctx.getBean(Recorder.class);
        H2Client client = new H2Client();
        Channel channel = connectHttp2(client);
        try {
            Http2FrameStream stream = request(channel, client, path);
            recorder.pending(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertFalse(recorder.abandoned(id).isDone());
            channel.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream)).sync();
            assertAbandoned(recorder, id);
            assertTrue(channel.isActive(), "the connection stays open");
        } finally {
            channel.close().sync();
        }
    }

    private static void assertAbandoned(Recorder recorder, String id) throws Exception {
        recorder.abandoned(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        // a failing callback does not keep the next one from running
        assertTrue(recorder.abandonedAfterFailure(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        // the completion is settled although the route has not answered
        Throwable failure = recorder.completion(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElseThrow();
        assertInstanceOf(ConnectionClosedException.class, failure);
        CompletableFuture<HttpResponse<?>> pending = recorder.pending(id).getNow(null);
        assertFalse(pending.isDone(), "the route decides what happens to its work");
        assertFalse(recorder.removedRan(id).get(), "a removed callback does not run");
        // a late answer is dropped
        pending.complete(HttpResponse.ok("late"));
    }

    private static Throwable failure(ApplicationContext context, String id) throws Exception {
        return context.getBean(Recorder.class).completion(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElse(null);
    }

    private static int get(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            java.net.http.HttpResponse<InputStream> response = client.send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                body.readAllBytes();
            }
            return response.statusCode();
        }
    }

    private static void rawExchange(String request) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout((int) TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            // the server closes the connection after the response
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
            assertTrue(response.startsWith("HTTP/1."), response);
        }
    }

    private static String readUntil(InputStream in, String marker) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!received.toString(StandardCharsets.US_ASCII).contains(marker)) {
            int n = in.read(buffer);
            if (n < 0) {
                break;
            }
            received.write(buffer, 0, n);
        }
        return received.toString(StandardCharsets.US_ASCII);
    }

    private static Channel connectHttp2(H2Client client) throws InterruptedException {
        return new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .remoteAddress(h2Server.getHost(), h2Server.getPort())
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), client);
                }
            })
            .connect().sync().channel();
    }

    private static Http2FrameStream request(Channel channel, H2Client client, String path) throws Exception {
        CompletableFuture<Http2FrameStream> written = new CompletableFuture<>();
        channel.eventLoop().execute(() -> {
            Http2FrameStream stream = client.newStream();
            DefaultHttp2Headers headers = new DefaultHttp2Headers();
            headers.method("GET").scheme("http").authority("localhost").path(path);
            channel.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(stream));
            written.complete(stream);
        });
        return written.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Signals the first data frame and the reset of a stream.
     */
    static final class H2Client extends Http2ChannelDuplexHandler {
        final CompletableFuture<Void> firstData = new CompletableFuture<>();
        final CompletableFuture<Void> reset = new CompletableFuture<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof Http2DataFrame) {
                    firstData.complete(null);
                } else if (msg instanceof Http2ResetFrame) {
                    reset.complete(null);
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final Map<String, ServerHttpRequest<?>> requests = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<Optional<Throwable>>> completions = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<Void>> abandoned = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<Boolean>> abandonedAfterFailure = new ConcurrentHashMap<>();
        final Map<String, AtomicBoolean> removedRan = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<Void>> gates = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<CompletableFuture<HttpResponse<?>>>> pending = new ConcurrentHashMap<>();

        CompletableFuture<Optional<Throwable>> completion(String id) {
            return completions.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        CompletableFuture<Void> abandoned(String id) {
            return abandoned.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        CompletableFuture<Boolean> abandonedAfterFailure(String id) {
            return abandonedAfterFailure.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        AtomicBoolean removedRan(String id) {
            return removedRan.computeIfAbsent(id, k -> new AtomicBoolean());
        }

        CompletableFuture<Void> gate(String id) {
            return gates.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        CompletableFuture<CompletableFuture<HttpResponse<?>>> pending(String id) {
            return pending.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        String capture(HttpRequest<?> request) {
            String id = request.getParameters().get("id");
            requests.put(id, (ServerHttpRequest<?>) request);
            return id;
        }

        String register(HttpRequest<?> request) {
            String id = capture(request);
            ServerHttpRequest<?> serverRequest = (ServerHttpRequest<?>) request;
            assertTrue(serverRequest.whenResponseCompleted(failure -> completion(id).complete(Optional.ofNullable(failure))));
            assertNotNull(serverRequest.whenAbandoned(() -> abandoned(id).complete(null)));
            return id;
        }

        CompletableFuture<HttpResponse<?>> registerPending(HttpRequest<?> request) {
            String id = register(request);
            ServerHttpRequest<?> serverRequest = (ServerHttpRequest<?>) request;
            serverRequest.whenAbandoned(() -> {
                throw new IllegalStateException("abandonment callback failed");
            });
            serverRequest.whenAbandoned(() -> abandonedAfterFailure(id).complete(true));
            Runnable remove = serverRequest.whenAbandoned(() -> removedRan(id).set(true));
            assertNotNull(remove);
            remove.run();
            CompletableFuture<HttpResponse<?>> future = new CompletableFuture<>();
            pending(id).complete(future);
            return future;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final Recorder recorder;

        Routes(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/handler/ok").handle((request, pathVariables) -> {
                recorder.register(request);
                return HttpResponse.ok("ok").contentType(MediaType.TEXT_PLAIN_TYPE);
            });
            routes.GET("/handler/unregistered").handle((request, pathVariables) -> {
                recorder.capture(request);
                return HttpResponse.ok("ok").contentType(MediaType.TEXT_PLAIN_TYPE);
            });
            routes.GET("/handler/error").handle((request, pathVariables) -> {
                recorder.register(request);
                throw new IllegalStateException("route failed");
            });
            routes.GET("/handler/large").handle((request, pathVariables) -> {
                recorder.register(request);
                return HttpResponse.ok(new byte[LARGE_BODY]).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
            });
            routes.GET("/handler/large-abandon-only").handle((request, pathVariables) -> {
                String id = recorder.capture(request);
                assertNotNull(((ServerHttpRequest<?>) request).whenAbandoned(() -> recorder.abandoned(id).complete(null)));
                return HttpResponse.ok(new byte[LARGE_BODY]).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
            });
            routes.GET("/handler/bad-te").handle((request, pathVariables) -> {
                recorder.register(request);
                // the quoted value cannot be converted to HTTP/2 headers
                return HttpResponse.noContent().header("te", "\"a,b");
            });
            routes.GET("/handler/pending").handleAsync((request, pathVariables) -> recorder.registerPending(request));
        }
    }

    @Controller("/callbacks")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CallbacksController {
        private final Recorder recorder;

        CallbacksController(Recorder recorder) {
            this.recorder = recorder;
        }

        @Get(value = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok(HttpRequest<?> request) {
            recorder.register(request);
            return "ok";
        }

        @Get(value = "/rewritten", produces = MediaType.TEXT_PLAIN)
        String rewritten(HttpRequest<?> request) {
            recorder.register(request);
            return "ok";
        }

        @Get(value = "/rewritten-error", produces = MediaType.TEXT_PLAIN)
        String rewrittenError(HttpRequest<?> request) {
            recorder.register(request);
            throw new IllegalStateException("rewritten route failed");
        }

        @Get(value = "/late-error", produces = MediaType.TEXT_PLAIN)
        String lateError() {
            throw new IllegalStateException("late route failed");
        }

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        Publisher<String> stream(HttpRequest<?> request) {
            String id = recorder.register(request);
            // fails only after the client received the head and the first chunk
            return Flux.just("first").concatWith(Mono.fromFuture(recorder.gate(id))
                .then(Mono.error(new IllegalStateException("body failed"))));
        }

        @Get("/handled")
        String handled(HttpRequest<?> request) {
            recorder.register(request);
            throw new HandledException();
        }

        @Error(HandledException.class)
        HttpResponse<?> onHandled() {
            return HttpResponse.badRequest();
        }

        @Get(value = "/replaced-error", produces = MediaType.TEXT_PLAIN)
        String replacedError() {
            throw new IllegalStateException("replaced route failed");
        }

        @Get(value = "/writer-handled", produces = MediaType.TEXT_PLAIN)
        FailingBody writerHandled(HttpRequest<?> request) {
            recorder.register(request);
            return new FailingBody(true);
        }

        @Get(value = "/writer-unhandled", produces = MediaType.TEXT_PLAIN)
        FailingBody writerUnhandled(HttpRequest<?> request) {
            recorder.register(request);
            return new FailingBody(false);
        }

        @Error(HandledWriterException.class)
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> onHandledWriter() {
            return HttpResponse.<String>status(HttpStatus.CONFLICT).body("handled");
        }

        @Get("/pending")
        CompletableFuture<HttpResponse<?>> pending(HttpRequest<?> request) {
            return recorder.registerPending(request);
        }
    }

    @ServerFilter("/callbacks/rewritten**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RewritingFilter {
        @RequestFilter
        void rewrite(MutableHttpRequest<?> request) {
            request.uri(URI.create(request.getUri() + "&rewritten=true"));
        }
    }

    @ServerFilter("/callbacks/late-error")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class LateRegistrationFilter {
        private final Recorder recorder;

        LateRegistrationFilter(Recorder recorder) {
            this.recorder = recorder;
        }

        @ResponseFilter
        void register(HttpRequest<?> request) {
            String id = request.getParameters().get("id");
            assertTrue(((ServerHttpRequest<?>) request).whenResponseCompleted(failure -> recorder.completion(id).complete(Optional.ofNullable(failure))));
        }
    }

    static final class HandledException extends RuntimeException {
    }

    static final class HandledWriterException extends RuntimeException {
    }

    record FailingBody(boolean handled) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class FailingWriter implements MessageBodyWriter<FailingBody> {
        @Override
        public void writeTo(Argument<FailingBody> type, MediaType mediaType, FailingBody object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            if (object.handled()) {
                throw new HandledWriterException();
            }
            throw new IllegalStateException("unhandled writer failure");
        }
    }

    @Filter("/callbacks/replaced-error")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplacingFilter implements HttpServerFilter {
        private final Recorder recorder;

        ReplacingFilter(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            recorder.register(request);
            // continues with a request that has nothing to do with the received one
            return chain.proceed(HttpRequest.GET(request.getPath()));
        }
    }
}
