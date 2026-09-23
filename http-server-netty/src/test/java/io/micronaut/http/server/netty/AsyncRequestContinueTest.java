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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An asynchronous handler that reads the body of a request that expects {@code 100 Continue}
 * gets the body after the server answered {@code 100 Continue}; a handler that answers without
 * reading the body never makes the server ask the client for it.
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

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes continueRoutes() {
            return routes -> {
                routes.asyncPOST("/continue/reject", (request, pathVariables) ->
                    CompletableFuture.completedFuture(HttpResponse.unauthorized())).consumesAll();
                routes.asyncPOST("/continue/read", (request, pathVariables) ->
                    request.text().thenApply(text -> HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE))).consumesAll();
            };
        }
    }
}
