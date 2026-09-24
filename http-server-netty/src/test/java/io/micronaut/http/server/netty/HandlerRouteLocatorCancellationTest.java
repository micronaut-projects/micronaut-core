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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The stage of an asynchronous route locator that has not located its target yet is cancelled
 * when the client closes the connection: the server stops waiting for a target no one will
 * receive a response for, and keeps serving.
 */
class HandlerRouteLocatorCancellationTest {
    private static final String SPEC_NAME = "HandlerRouteLocatorCancellationTest";
    private static final String ORIGIN = "https://foo.com";
    private static final long TIMEOUT_MILLIS = 10_000;

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static Stages stages;

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
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aPendingLocatorIsCancelledWhenTheClientCloses() throws Exception {
        abandon("request", "GET /pending/request/items HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertServes();
    }

    @Test
    void aPendingLocatorOfAPreflightIsCancelledWhenTheClientCloses() throws Exception {
        abandon("preflight", "OPTIONS /pending/preflight/items HTTP/1.1\r\nHost: localhost\r\nOrigin: " + ORIGIN
            + "\r\nAccess-Control-Request-Method: GET\r\n\r\n");
        assertServes();
    }

    private static void abandon(String id, String request) throws Exception {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            await("the locator of " + id + " runs", () -> stages.stages.containsKey(id));
        }
        CompletableFuture<Object> stage = stages.stages.get(id);
        await("the stage of the locator of " + id + " is cancelled", stage::isCancelled);
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
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PendingRoutes implements HttpRoutes {
        private final RouteTableFactory tables;
        private final Stages stages;
        private final ExecutorService executor;

        PendingRoutes(RouteTableFactory tables, Stages stages, @Named(TaskExecutors.IO) ExecutorService executor) {
            this.tables = tables;
            this.stages = stages;
            this.executor = executor;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            RouteTable items = tables.buildLocatedHttpRoutes(located -> located.GET("/items", (request, pathVariables) ->
                HttpResponse.ok("item").contentType(MediaType.TEXT_PLAIN_TYPE)));
            // never completes on its own
            routes.locateAsync("/pending/{id}", (request, pathVariables) ->
                stages.stages.computeIfAbsent(pathVariables.getString("id"), id -> new CompletableFuture<>()), target -> items);
            routes.locateAsync("/located/{id}", (request, pathVariables) -> CompletableFuture.supplyAsync(() -> "target", executor), target -> items);
        }
    }
}
