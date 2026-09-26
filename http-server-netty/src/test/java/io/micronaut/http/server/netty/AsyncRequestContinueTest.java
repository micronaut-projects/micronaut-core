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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A controller or filter method that reads the body of a request that expects
 * {@code 100 Continue} with an {@link AsyncRequestBody} gets the body after the server answered
 * {@code 100 Continue}; a method that answers without reading the body, or that does not receive
 * it, never makes the server ask the client for it.
 */
class AsyncRequestContinueTest {
    private static final String SPEC_NAME = "AsyncRequestContinueTest";

    @Test
    void aHandlerThatRejectsWithoutReadingNeverAsksForTheBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                out.write(headers(server, "/continue/reject", 5).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // the body is never sent: the response must not wait for it
                String head = readHead(socket.getInputStream());
                assertTrue(head.startsWith("HTTP/1.1 401 "), head);
            }
        }
    }

    @Test
    void aHandlerWithoutTheBodyNeverAsksForIt() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                out.write(headers(server, "/continue/no-body", 5).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // the handler does not claim the body: the response is not a 100 Continue, and does not wait for the body
                String head = readHead(in);
                assertTrue(head.startsWith("HTTP/1.1 200 "), head);
                assertEquals("no body", new String(in.readNBytes(7), StandardCharsets.US_ASCII));
            }
        }
    }

    @Test
    void aHandlerThatReadsTheBodyAsksForIt() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                out.write(headers(server, "/continue/read", 5).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String interim = readHead(in);
                assertTrue(interim.startsWith("HTTP/1.1 100 "), interim);
                out.write("hello".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String head = readHead(in);
                assertTrue(head.startsWith("HTTP/1.1 200 "), head);
                assertEquals("hello", new String(in.readNBytes(5), StandardCharsets.US_ASCII));
            }
        }
    }

    @Test
    void aFilterThatReadsTheBodyAsksForIt() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                out.write(headers(server, "/continue/filtered", 5).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String interim = readHead(in);
                assertTrue(interim.startsWith("HTTP/1.1 100 "), interim);
                out.write("hello".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String head = readHead(in);
                assertTrue(head.startsWith("HTTP/1.1 200 "), head);
                assertEquals("hello", new String(in.readNBytes(5), StandardCharsets.US_ASCII));
            }
        }
    }

    private static String headers(EmbeddedServer server, String path, int length) {
        return "POST " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Length: " + length + "\r\n"
            + "Expect: 100-continue\r\n"
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

    @Controller("/continue")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ContinueController {
        @Post(uri = "/reject", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<?>> reject(AsyncRequestBody body) {
            return CompletableFuture.completedFuture(HttpResponse.unauthorized());
        }

        @Post(uri = "/no-body", consumes = MediaType.ALL)
        HttpResponse<String> noBody() {
            return HttpResponse.ok("no body").contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Post(uri = "/read", consumes = MediaType.ALL)
        CompletionStage<HttpResponse<String>> read(AsyncRequestBody body) {
            return body.text().thenApply(text -> HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @ServerFilter("/continue/filtered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ContinueFilter {
        @RequestFilter
        CompletionStage<@Nullable HttpResponse<?>> filter(HttpRequest<?> request, AsyncRequestBody body) {
            return body.text().thenApply(text -> {
                request.setAttribute("continue-filter", text);
                return null;
            });
        }
    }

    @Controller("/continue/filtered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FilteredController {
        @Post(consumes = MediaType.ALL)
        HttpResponse<String> read(HttpRequest<?> request) {
            return HttpResponse.ok(request.getAttribute("continue-filter", String.class).orElse("none")).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
