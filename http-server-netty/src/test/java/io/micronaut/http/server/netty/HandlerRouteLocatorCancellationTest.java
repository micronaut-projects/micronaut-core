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
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The server stops waiting for the stage of an asynchronous route locator that has not located
 * its target yet when the client closes the connection: no one will receive a response for the
 * request, and the server keeps serving. The stage is not cancelled, as the locator may share it
 * with other requests, and the abandoned request is not logged as an error.
 */
class HandlerRouteLocatorCancellationTest {
    private static final String SPEC_NAME = "HandlerRouteLocatorCancellationTest";
    private static final String ORIGIN = "https://foo.com";
    private static final long TIMEOUT_MILLIS = 10_000;

    private static final ListAppender<ILoggingEvent> LOGS = new ListAppender<>();

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static Stages stages;
    private static @Nullable Level level;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.cors.enabled", true,
            "micronaut.server.cors.configurations.web.allowed-origins", List.of(ORIGIN)
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
        stages = ctx.getBean(Stages.class);
        // an abandoned request is logged at the debug level, like a request whose connection closed
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
        if (ctx != null) {
            ctx.close();
        }
    }

    private static Logger routeExecutorLogger() {
        return (Logger) LoggerFactory.getLogger(RouteExecutor.class);
    }

    /**
     * @return The logged events of the requests the server stopped waiting for
     */
    private static List<ILoggingEvent> abandonments() {
        synchronized (LOGS) {
            return LOGS.list.stream()
                .filter(event -> event.getThrowableProxy() != null && event.getThrowableProxy().getClassName().endsWith("LocationAbandoned"))
                .toList();
        }
    }

    @Test
    void aPendingLocatorIsAbandonedWhenTheClientCloses() throws Exception {
        abandon("request", "GET /pending/request/items HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertServes();
    }

    @Test
    void aPendingLocatorOfAPreflightIsAbandonedWhenTheClientCloses() throws Exception {
        abandon("preflight", "OPTIONS /pending/preflight/items HTTP/1.1\r\nHost: localhost\r\nOrigin: " + ORIGIN
            + "\r\nAccess-Control-Request-Method: GET\r\n\r\n");
        assertServes();
    }

    @Test
    void anAbandonedRequestIsNotLoggedAsAnError() throws Exception {
        abandon("logged", "GET /pending/logged/items HTTP/1.1\r\nHost: localhost\r\n\r\n");
        List<ILoggingEvent> abandonments = abandonments();
        assertTrue(abandonments.stream().allMatch(event -> event.getLevel() == Level.DEBUG), abandonments::toString);
    }

    @Test
    void aStageSharedWithAnotherRequestStaysUsable() throws Exception {
        try (Socket waiting = new Socket("localhost", server.getPort())) {
            waiting.setSoTimeout((int) TIMEOUT_MILLIS);
            OutputStream waitingOut = waiting.getOutputStream();
            waitingOut.write("GET /pending/shared/items HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            waitingOut.flush();
            await("the locator of the waiting request runs", () -> stages.stages.containsKey("shared"));
            // a second request for the same target, which the client abandons
            abandon("shared", "GET /pending/shared/items HTTP/1.1\r\nHost: localhost\r\n\r\n", 2);

            CompletableFuture<Object> stage = stages.stages.get("shared");
            assertTrue(stage.complete("target"));
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            waiting.getInputStream().transferTo(response);
            String text = response.toString(StandardCharsets.US_ASCII);
            assertTrue(text.startsWith("HTTP/1.1 200"), text);
            assertTrue(text.endsWith("item"), text);
        }
    }

    private static void abandon(String id, String request) throws Exception {
        abandon(id, request, 1);
    }

    /**
     * Send a request whose locator waits for the stage of the id, close the connection, and wait
     * until the server stopped waiting for the stage: the request is abandoned, and the stage is
     * not cancelled.
     *
     * @param calls The calls of the locator of the id once it ran for the request
     */
    private static void abandon(String id, String request, int calls) throws Exception {
        int abandoned = abandonments().size();
        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            await("the locator of " + id + " runs", () -> stages.calls(id) >= calls);
        }
        await("the request of " + id + " is abandoned", () -> abandonments().size() > abandoned);
        assertFalse(stages.stages.get(id).isCancelled(), "the stage of " + id + " is not cancelled");
    }

    private static void assertServes() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            socket.setSoTimeout((int) TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write("GET /located/1/items HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            in.transferTo(response);
            String text = response.toString(StandardCharsets.US_ASCII);
            assertTrue(text.startsWith("HTTP/1.1 200"), text);
            assertTrue(text.endsWith("item"), text);
        }
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
    static class Stages {
        final Map<String, CompletableFuture<Object>> stages = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

        CompletableFuture<Object> locate(String id) {
            calls.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
            // never completes on its own, shared by the requests of the id
            return stages.computeIfAbsent(id, key -> new CompletableFuture<>());
        }

        int calls(String id) {
            AtomicInteger count = calls.get(id);
            return count == null ? 0 : count.get();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PendingRoutes implements HttpRoutes {
        private final Stages stages;
        private final ExecutorService executor;

        PendingRoutes(Stages stages, @Named(TaskExecutors.IO) ExecutorService executor) {
            this.stages = stages;
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            ItemRoutes items = new ItemRoutes();
            routes.locateAsync("/pending/{id}", (request, pathVariables) -> stages.locate(pathVariables.getString("id")), target -> items);
            routes.locateAsync("/located/{id}", (request, pathVariables) -> CompletableFuture.supplyAsync(() -> "target", executor), items);
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
