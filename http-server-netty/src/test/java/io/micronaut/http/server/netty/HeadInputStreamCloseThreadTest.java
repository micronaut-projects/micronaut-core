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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing the InputStream body of a HEAD response may block, so it must not run on the event loop.
 */
class HeadInputStreamCloseThreadTest {
    private static final String SPEC_NAME = "HeadInputStreamCloseThreadTest";
    static volatile CompletableFuture<Thread> closedOn = new CompletableFuture<>();

    @Test
    void headClosesTheStreamOffTheEventLoop() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            closedOn = new CompletableFuture<>();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(10_000);
                socket.getOutputStream().write("HEAD /head-stream HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
            }
            Thread thread = closedOn.get(5, TimeUnit.SECONDS);
            assertFalse(thread instanceof FastThreadLocalThread && thread.getName().contains("eventLoop"), "closed on " + thread);
        }
    }

    @Test
    void headClosesTheStreamOfAStreamedFile() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            closedOn = new CompletableFuture<>();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(10_000);
                socket.getOutputStream().write("HEAD /head-streamed-file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
            }
            Thread thread = closedOn.get(5, TimeUnit.SECONDS);
            assertFalse(thread instanceof FastThreadLocalThread && thread.getName().contains("eventLoop"), "closed on " + thread);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Get(value = "/head-streamed-file", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<StreamedFile> streamedFile() {
            return CompletableFuture.completedFuture(new StreamedFile(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    closedOn.complete(Thread.currentThread());
                    super.close();
                }
            }, MediaType.TEXT_PLAIN_TYPE));
        }

        @Get(value = "/head-stream", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<InputStream> stream() {
            // completes on the event loop, like a non-blocking async controller
            return CompletableFuture.completedFuture(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    closedOn.complete(Thread.currentThread());
                    super.close();
                }
            });
        }
    }
}
