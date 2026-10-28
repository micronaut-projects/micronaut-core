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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A filter that buffers the body of a request that expects {@code 100 Continue} with
 * {@link AsyncRequestBody#of} makes the server ask the client for the body, and the route reads it
 * after the filter; a filter that answers without buffering never asks for it.
 */
class AsyncRequestBodyOfContinueTest {
    private static final String SPEC_NAME = "AsyncRequestBodyOfContinueTest";
    private static final int SIZE = 200 * 1024;

    @Test
    void aFilterThatBuffersALargeBodyAsksForIt() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                out.write(headers(server, "/buffer-continue", SIZE, null).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String interim = readHead(in);
                assertTrue(interim.startsWith("HTTP/1.1 100 "), interim);
                byte[] body = new byte[SIZE];
                Arrays.fill(body, (byte) 'a');
                out.write(body);
                out.flush();
                String head = readHead(in);
                assertTrue(head.startsWith("HTTP/1.1 200 "), head);
                String expected = SIZE + "/" + SIZE;
                assertEquals(expected, new String(in.readNBytes(expected.length()), StandardCharsets.US_ASCII));
            }
        }
    }

    @Test
    void aFilterThatRejectsWithoutBufferingNeverAsksForTheBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                out.write(headers(server, "/buffer-continue", SIZE, "X-Reject: yes\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // the body is never sent: the response must not wait for it
                String head = readHead(socket.getInputStream());
                assertTrue(head.startsWith("HTTP/1.1 401 "), head);
            }
        }
    }

    private static String headers(EmbeddedServer server, String path, int length, @Nullable String extra) {
        return "POST " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Length: " + length + "\r\n"
            + "Expect: 100-continue\r\n"
            + (extra == null ? "" : extra)
            + "\r\n";
    }

    /**
     * Read a response head, up to the empty line.
     */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        return head.toString(StandardCharsets.US_ASCII);
    }

    @ServerFilter("/buffer-continue")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BufferingFilter {
        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> filter(HttpRequest<?> request) {
            if (request.getHeaders().contains("X-Reject")) {
                return CompletableFuture.completedFuture(HttpResponse.unauthorized());
            }
            return AsyncRequestBody.of(request).copy().bytes(Integer.MAX_VALUE).thenApply(bytes -> {
                request.setAttribute("buffered", bytes.length);
                return null;
            });
        }
    }

    @Controller("/buffer-continue")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BufferedController {
        @Post(consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String read(HttpRequest<?> request, @Body String body) {
            return request.getAttribute("buffered", Integer.class).orElse(-1) + "/" + body.length();
        }
    }
}
