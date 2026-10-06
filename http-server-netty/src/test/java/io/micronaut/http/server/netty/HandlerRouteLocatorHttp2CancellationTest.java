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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
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
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Over HTTP/2, the stage of an asynchronous route locator that has not located its target yet is
 * cancelled when the client resets the stream of the request, while the connection stays open:
 * the cancellation follows the stream, not the connection.
 */
class HandlerRouteLocatorHttp2CancellationTest {
    private static final String SPEC_NAME = "HandlerRouteLocatorHttp2CancellationTest";
    private static final long TIMEOUT_MILLIS = 10_000;

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static Stages stages;
    private static EventLoopGroup group;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
        stages = ctx.getBean(Stages.class);
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
    }

    @Test
    void aPendingLocatorIsCancelledWhenTheClientResetsTheStream() throws Exception {
        Client client = new Client();
        Channel channel = connect(client);
        try {
            for (String id : new String[]{"first", "second", "third"}) {
                Http2FrameStream stream = request(channel, client, "/pending/" + id + "/items");
                await("the locator of " + id + " runs", () -> stages.stages.containsKey(id));
                CompletableFuture<Object> stage = stages.stages.get(id);
                assertFalse(stage.isDone());
                channel.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream)).sync();
                await("the stage of the locator of " + id + " is cancelled", stage::isCancelled);
            }
            assertTrue(channel.isActive(), "the connection stays open");

            // the connection keeps serving
            CompletableFuture<String> body = client.body();
            request(channel, client, "/located/1/items");
            assertEquals("item", body.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            channel.close().sync();
        }
    }

    @Test
    void aPendingLocatorIsCancelledWhenTheClientClosesTheConnection() throws Exception {
        Client client = new Client();
        Channel channel = connect(client);
        request(channel, client, "/pending/closed/items");
        await("the locator of closed runs", () -> stages.stages.containsKey("closed"));
        channel.close().sync();
        CompletableFuture<Object> stage = stages.stages.get("closed");
        await("the stage of the locator of closed is cancelled", stage::isCancelled);
    }

    private static Channel connect(Client client) throws InterruptedException {
        return new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .remoteAddress(server.getHost(), server.getPort())
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), client);
                }
            })
            .connect().sync().channel();
    }

    private static Http2FrameStream request(Channel channel, Client client, String path) throws Exception {
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

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting until " + what);
            }
            Thread.sleep(20);
        }
    }

    /**
     * Gives the body of the next response.
     */
    static final class Client extends Http2ChannelDuplexHandler {
        private final StringBuilder received = new StringBuilder();
        private volatile CompletableFuture<String> body = new CompletableFuture<>();

        CompletableFuture<String> body() {
            CompletableFuture<String> next = new CompletableFuture<>();
            body = next;
            return next;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                if (msg instanceof Http2DataFrame data) {
                    received.append(data.content().toString(StandardCharsets.UTF_8));
                    if (data.isEndStream()) {
                        body.complete(received.toString());
                        received.setLength(0);
                    }
                } else if (msg instanceof Http2HeadersFrame headers && headers.isEndStream()) {
                    body.complete(received.toString());
                    received.setLength(0);
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Stages {
        final Map<String, CompletableFuture<Object>> stages = new ConcurrentHashMap<>();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PendingRoutes implements HttpRoutes {
        private final Stages stages;

        PendingRoutes(Stages stages) {
            this.stages = stages;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            ItemRoutes items = new ItemRoutes();
            // never completes on its own
            routes.locateAsync("/pending/{id}", (request, pathVariables) ->
                stages.stages.computeIfAbsent(pathVariables.getString("id"), id -> new CompletableFuture<>()), target -> items);
            routes.locateAsync("/located/{id}", (request, pathVariables) -> CompletableFuture.completedFuture("target"), items);
        }
    }

    static final class ItemRoutes implements LocatedRoutes<Object> {
        @Override
        public Argument<Object> targetType() {
            return Argument.OBJECT_ARGUMENT;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Object> located) {
            located.GET("/items", (request, pathVariables) -> HttpResponse.ok("item").contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
