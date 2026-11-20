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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameStream;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Over HTTP/2, a request whose route locator waits for the body is abandoned when its stream is
 * reset, or its connection closed, before the body arrived: the locator is never called, and
 * the connection keeps serving the other streams.
 */
class HandlerRouteLocatorBodyHttp2CancellationTest {
    private static final String SPEC_NAME = "HandlerRouteLocatorBodyHttp2CancellationTest";
    private static final long TIMEOUT_MILLIS = 10_000;

    private static final ListAppender<ILoggingEvent> LOGS = new ListAppender<>();

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static Located located;
    private static EventLoopGroup group;
    private static @Nullable Level level;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
        located = ctx.getBean(Located.class);
        group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        Logger logger = routeExecutorLogger();
        level = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        LOGS.start();
        logger.addAppender(LOGS);
    }

    @AfterAll
    static void stop() {
        Logger logger = routeExecutorLogger();
        logger.detachAppender(LOGS);
        logger.setLevel(level);
        if (group != null) {
            group.shutdownGracefully();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aLocatorWaitingForTheFormIsAbandonedWhenTheClientResetsTheStream() throws Exception {
        HandlerRouteLocatorHttp2CancellationTest.Client client = new HandlerRouteLocatorHttp2CancellationTest.Client();
        Channel channel = connect(client);
        try {
            for (String id : new String[]{"first", "second", "third"}) {
                int abandoned = abandoned();
                Http2FrameStream stream = partialForm(channel, client, "/accounts/" + id + "/greet");
                channel.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream)).sync();
                await("the request of " + id + " ends", () -> abandoned() > abandoned || located.failed.contains(id));
                assertFalse(located.ids.contains(id), "the locator of " + id + " is not called");
            }
            assertTrue(channel.isActive(), "the connection stays open");
            assertServes(channel, client);
        } finally {
            channel.close().sync();
        }
    }

    @Test
    void aLocatorWaitingForTheFormIsAbandonedWhenTheClientClosesTheConnection() throws Exception {
        HandlerRouteLocatorHttp2CancellationTest.Client client = new HandlerRouteLocatorHttp2CancellationTest.Client();
        Channel channel = connect(client);
        int abandoned = abandoned();
        partialForm(channel, client, "/accounts/closed/greet");
        channel.close().sync();
        await("the request of closed ends", () -> abandoned() > abandoned || located.failed.contains("closed"));
        assertFalse(located.ids.contains("closed"), "the locator of closed is not called");

        // the server keeps serving
        HandlerRouteLocatorHttp2CancellationTest.Client next = new HandlerRouteLocatorHttp2CancellationTest.Client();
        Channel other = connect(next);
        try {
            assertServes(other, next);
        } finally {
            other.close().sync();
        }
    }

    @Test
    void aLocatorWaitingForASharedStageAfterTheFormWasReadIsAbandonedWhenTheClientResetsTheStream() throws Exception {
        HandlerRouteLocatorHttp2CancellationTest.Client client = new HandlerRouteLocatorHttp2CancellationTest.Client();
        Channel channel = connect(client);
        try {
            for (String id : new String[]{"one", "two"}) {
                int abandoned = abandoned();
                CompletableFuture<Http2FrameStream> written = new CompletableFuture<>();
                channel.eventLoop().execute(() -> {
                    Http2FrameStream stream = client.newStream();
                    DefaultHttp2Headers headers = new DefaultHttp2Headers();
                    headers.method("POST").scheme("http").authority("localhost").path("/shared/" + id + "/greet");
                    headers.set("content-type", MediaType.APPLICATION_FORM_URLENCODED);
                    channel.write(new DefaultHttp2HeadersFrame(headers, false).stream(stream));
                    channel.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("token=gold&name=Ann", StandardCharsets.UTF_8), true).stream(stream));
                    written.complete(stream);
                });
                Http2FrameStream stream = written.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                // the form was read: the locator waits for a stage shared by every request
                await("the locator of " + id + " reads the form", () -> located.ids.contains(id));
                channel.writeAndFlush(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream)).sync();
                await("the request of " + id + " is abandoned", () -> abandoned() > abandoned);
                assertFalse(located.shared.isDone(), "the shared stage is not cancelled");
            }
            assertTrue(channel.isActive(), "the connection stays open");
            assertServes(channel, client);
        } finally {
            channel.close().sync();
        }
    }

    /**
     * Send the headers and part of the form, and wait until the server reads the form.
     */
    private static Http2FrameStream partialForm(Channel channel, HandlerRouteLocatorHttp2CancellationTest.Client client, String path) throws Exception {
        CompletableFuture<Http2FrameStream> written = new CompletableFuture<>();
        channel.eventLoop().execute(() -> {
            Http2FrameStream stream = client.newStream();
            DefaultHttp2Headers headers = new DefaultHttp2Headers();
            headers.method("POST").scheme("http").authority("localhost").path(path);
            headers.set("content-type", MediaType.APPLICATION_FORM_URLENCODED);
            channel.write(new DefaultHttp2HeadersFrame(headers, false).stream(stream));
            channel.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("token=gold&na", StandardCharsets.UTF_8), false).stream(stream));
            written.complete(stream);
        });
        Http2FrameStream stream = written.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        await("the server reads the form of " + path, () -> located.reading.contains(path));
        return stream;
    }

    private static void assertServes(Channel channel, HandlerRouteLocatorHttp2CancellationTest.Client client) throws Exception {
        CompletableFuture<String> body = client.body();
        channel.eventLoop().execute(() -> {
            Http2FrameStream stream = client.newStream();
            DefaultHttp2Headers headers = new DefaultHttp2Headers();
            headers.method("POST").scheme("http").authority("localhost").path("/accounts/served/greet");
            headers.set("content-type", MediaType.APPLICATION_FORM_URLENCODED);
            channel.write(new DefaultHttp2HeadersFrame(headers, false).stream(stream));
            channel.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("token=gold&name=Ann", StandardCharsets.UTF_8), true).stream(stream));
        });
        assertEquals("served gold: hello Ann", body.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
    }

    private static Logger routeExecutorLogger() {
        return (Logger) LoggerFactory.getLogger(RouteExecutor.class);
    }

    /**
     * @return The number of requests the server stopped waiting for
     */
    private static int abandoned() {
        synchronized (LOGS) {
            return (int) LOGS.list.stream()
                .filter(event -> event.getThrowableProxy() != null && event.getThrowableProxy().getClassName().endsWith("LocationAbandoned"))
                .count();
        }
    }

    private static Channel connect(HandlerRouteLocatorHttp2CancellationTest.Client client) throws InterruptedException {
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

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting until " + what);
            }
            Thread.sleep(20);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Located {
        /**
         * The paths whose form the server started to read for the locator.
         */
        final Set<String> reading = ConcurrentHashMap.newKeySet();
        /**
         * The ids the locator was called for.
         */
        final Set<String> ids = ConcurrentHashMap.newKeySet();
        /**
         * The ids whose body failed to be read.
         */
        final Set<String> failed = ConcurrentHashMap.newKeySet();
        /**
         * A stage shared by every request, e.g. of a cache, that never completes.
         */
        final CompletableFuture<Object> shared = new CompletableFuture<>();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final Located located;

        Routes(Located located) {
            this.located = located;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            AccountRoutes accounts = new AccountRoutes();
            // the locator reads the body itself, to tell when the server reads it
            routes.locator("/accounts/{id}")
                .body()
                .locateAsync((request, pathVariables, body) -> {
                    String id = pathVariables.getString("id");
                    located.reading.add(request.getPath());
                    return body.form()
                        .whenComplete((form, error) -> {
                            if (error != null) {
                                located.failed.add(id);
                            }
                        })
                        .thenApply(form -> {
                            located.ids.add(id);
                            return id + " " + form.getString("token");
                        });
                }, accounts);
            routes.locator("/shared/{id}")
                .form()
                .locateAsync((request, pathVariables, form) -> {
                    located.ids.add(pathVariables.getString("id"));
                    return located.shared;
                }, target -> accounts);
        }
    }

    static final class AccountRoutes implements LocatedRoutes<String> {
        @Override
        public Argument<String> targetType() {
            return Argument.STRING;
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<String> located) {
            located.POST("/greet").form().handle((request, pathVariables, account, form) ->
                HttpResponse.ok(account + ": hello " + form.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
