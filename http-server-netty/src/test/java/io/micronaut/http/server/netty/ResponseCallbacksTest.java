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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals(200, get("/handler/ok?id=handler-ok"));
        assertNull(recorder.completion("handler-ok").get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElse(null));
    }

    @Test
    void controllerCompletedWithoutFailure() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals(200, get("/callbacks/ok?id=controller-ok"));
        assertNull(recorder.completion("controller-ok").get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElse(null));
    }

    @Test
    void routeErrorIsReported() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals(500, get("/handler/error?id=handler-error"));
        Throwable failure = recorder.completion("handler-error").get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElseThrow();
        assertEquals("route failed", failure.getMessage());
    }

    @Test
    void handledErrorIsNotReported() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        assertEquals(400, get("/callbacks/handled?id=controller-handled"));
        assertNull(recorder.completion("controller-handled").get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElse(null));
    }

    @Test
    void bodyFailureAfterTheResponseStartedIsReported() throws Exception {
        Recorder recorder = ctx.getBean(Recorder.class);
        try {
            get("/callbacks/stream?id=stream-error");
        } catch (Exception e) {
            // the response ends abruptly
        }
        Throwable failure = recorder.completion("stream-error").get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElseThrow();
        assertEquals("body failed", failure.getMessage());
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
        HandlerRouteLocatorHttp2CancellationTest.Client client = new HandlerRouteLocatorHttp2CancellationTest.Client();
        Channel channel = new Bootstrap()
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
        try {
            CompletableFuture<Http2FrameStream> written = new CompletableFuture<>();
            channel.eventLoop().execute(() -> {
                Http2FrameStream stream = client.newStream();
                DefaultHttp2Headers headers = new DefaultHttp2Headers();
                headers.method("GET").scheme("http").authority("localhost").path(path);
                channel.writeAndFlush(new DefaultHttp2HeadersFrame(headers, true).stream(stream));
                written.complete(stream);
            });
            Http2FrameStream stream = written.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
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
        CompletableFuture<HttpResponse<?>> pending = recorder.pending(id).getNow(null);
        assertFalse(pending.isDone(), "the route decides what happens to its work");
        // the late response is not written: the completion reports the closed connection
        pending.complete(HttpResponse.ok("late"));
        Throwable failure = recorder.completion(id).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).orElseThrow();
        assertInstanceOf(ConnectionClosedException.class, failure);
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

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final Map<String, ServerHttpRequest<?>> requests = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<Optional<Throwable>>> completions = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<Void>> abandoned = new ConcurrentHashMap<>();
        final Map<String, CompletableFuture<CompletableFuture<HttpResponse<?>>>> pending = new ConcurrentHashMap<>();

        CompletableFuture<Optional<Throwable>> completion(String id) {
            return completions.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        CompletableFuture<Void> abandoned(String id) {
            return abandoned.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        CompletableFuture<CompletableFuture<HttpResponse<?>>> pending(String id) {
            return pending.computeIfAbsent(id, k -> new CompletableFuture<>());
        }

        String register(HttpRequest<?> request) {
            String id = request.getParameters().get("id");
            ServerHttpRequest<?> serverRequest = (ServerHttpRequest<?>) request;
            requests.put(id, serverRequest);
            assertTrue(serverRequest.whenResponseCompleted(failure -> completion(id).complete(Optional.ofNullable(failure))));
            return id;
        }

        CompletableFuture<HttpResponse<?>> registerPending(HttpRequest<?> request) {
            String id = register(request);
            CompletableFuture<HttpResponse<?>> future = new CompletableFuture<>();
            Runnable remove = ((ServerHttpRequest<?>) request).whenAbandoned(() -> abandoned(id).complete(null));
            assertTrue(remove != null);
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
            routes.GET("/handler/error").handle((request, pathVariables) -> {
                recorder.register(request);
                throw new IllegalStateException("route failed");
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

        @Get(value = "/stream", produces = MediaType.TEXT_PLAIN)
        Publisher<String> stream(HttpRequest<?> request) {
            recorder.register(request);
            return Flux.just("first").concatWith(Flux.error(new IllegalStateException("body failed")));
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

        @Get("/pending")
        CompletableFuture<HttpResponse<?>> pending(HttpRequest<?> request) {
            return recorder.registerPending(request);
        }
    }

    static final class HandledException extends RuntimeException {
    }
}
