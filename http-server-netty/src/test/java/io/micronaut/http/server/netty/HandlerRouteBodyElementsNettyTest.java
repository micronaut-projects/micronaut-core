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
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.TypedMessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.util.internal.ThreadExecutorMap;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Netty specifics of a {@link BodyElements} body: a blocking writer of the elements runs on a
 * worker thread, not on the event loop, like the writer of the elements of a publisher body.
 */
class HandlerRouteBodyElementsNettyTest {

    private static final String SPEC_NAME = "HandlerRouteBodyElementsNettyTest";

    @Test
    void blockingWriterOfTheElementsRunsOnAWorker() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            String response = get(server, "/netty-elements/blocking");
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            // each element in its own chunk or not: both were written on a worker
            assertEquals(2, response.split("worker,", -1).length - 1, response);
            assertFalse(response.contains("event-loop"), response);
        }
    }

    private static String get(EmbeddedServer server, String path) throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            socket.setSoTimeout(20_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record Blocking() {
    }

    /**
     * Writes the thread it runs on.
     */
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BlockingWriter implements TypedMessageBodyWriter<Blocking> {
        @Override
        public Argument<Blocking> getType() {
            return Argument.of(Blocking.class);
        }

        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public void writeTo(Argument<Blocking> type, MediaType mediaType, Blocking value, MutableHeaders headers, OutputStream out) throws CodecException {
            try {
                out.write((ThreadExecutorMap.currentExecutor() == null ? "worker," : "event-loop,").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Cannot write", e);
            }
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes nettyElementRoutes() {
            return routes -> routes.GET("/netty-elements/blocking", (request, pathVariables) -> {
                var elements = List.of(new Blocking(), new Blocking()).iterator();
                return HttpResponse.ok(BodyElements.of(() ->
                    CompletableFuture.completedFuture(elements.hasNext() ? Optional.of(elements.next()) : Optional.<Blocking>empty())))
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
            });
        }
    }
}
