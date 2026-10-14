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
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The body conditions of handler routes on bodies that arrive in parts: the body is read up to the
 * limit of the conditions and no further, the route chosen reads the whole body, and a client
 * that abandons the body while it is read leaks nothing.
 */
class HandlerRouteBodyConditionsStreamingTest {
    private static final String SPEC_NAME = "HandlerRouteBodyConditionsStreamingTest";
    private static final long TIMEOUT_MILLIS = 10_000;
    private static final AtomicInteger EVALUATED = new AtomicInteger();
    private static final AtomicInteger FILTERED = new AtomicInteger();

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            // the reset of an abandoned body is not always delivered on the loopback of macOS:
            // the server finds the dead connection by its idle timeout
            "micronaut.server.idle-timeout", "3s"
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aChunkedBodyWithinTheLimitIsMatched() throws Exception {
        assertEquals("matched match-small", chunked("/bcs/items", "match", "-small"));
    }

    @Test
    void aChunkedBodyLargerThanTheLimitGoesToTheNextRouteWhichReadsItAll() throws Exception {
        String large = "match" + "x".repeat(95);
        assertEquals("fallback 100", chunked("/bcs/items", large.substring(0, 40), large.substring(40)));
        assertEquals("404", chunked("/bcs/only", large.substring(0, 40), large.substring(40)));
    }

    @Test
    void aBodyWhoseLengthIsLargerThanTheLimitIsNotRead() throws Exception {
        int evaluated = EVALUATED.get();
        assertEquals("fallback 100", fixed("/bcs/items", "match" + "x".repeat(95)));
        assertEquals(evaluated, EVALUATED.get(), "the condition is not evaluated");
        assertEquals("matched match", fixed("/bcs/items", "match"));
    }

    @Test
    void aClientThatAbandonsTheBodyWhileItIsReadLeaksNothing() throws Exception {
        int evaluated = EVALUATED.get();
        int filtered = FILTERED.get();
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(head("/bcs/large", "Transfer-Encoding: chunked"));
            out.write(chunk("match" + "x".repeat(1000)));
            out.flush();
            await("the request reaches the server", () -> FILTERED.get() > filtered);
            Thread.sleep(100);
            // abruptly: a reset, not a graceful close
            socket.setSoLinger(true, 0);
        }
        assertEquals(evaluated, EVALUATED.get(), "the condition never saw a body that did not arrive");
        // the server keeps serving
        assertEquals("matched match", fixed("/bcs/items", "match"));
    }

    private static String chunked(String path, String... parts) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(head(path, "Transfer-Encoding: chunked"));
            out.flush();
            for (String part : parts) {
                out.write(chunk(part));
                out.flush();
                // the parts arrive apart: the body streams
                Thread.sleep(50);
            }
            out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return response(socket.getInputStream());
        }
    }

    private static String fixed(String path, String body) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            out.write(head(path, "Content-Length: " + bytes.length));
            out.write(bytes);
            out.flush();
            return response(socket.getInputStream());
        }
    }

    private static byte[] head(String path, String framing) {
        return ("POST " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: text/plain\r\n"
            + "Connection: close\r\n"
            + framing + "\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] chunk(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(bytes);
        out.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    /**
     * @return The body of a 200 response, or the status of another
     */
    private static String response(InputStream in) throws IOException {
        String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        String status = response.substring("HTTP/1.1 ".length(), "HTTP/1.1 ".length() + 3);
        if (!status.equals("200")) {
            return status;
        }
        int bodyStart = response.indexOf("\r\n\r\n");
        assertTrue(bodyStart > 0, response);
        return response.substring(bodyStart + 4);
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
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.serverFilter("/bcs/**").preMatching().before(request -> {
                FILTERED.incrementAndGet();
            });
            routes.POST("/bcs/items")
                .where(RouteCondition.bodyText(text -> {
                    EVALUATED.incrementAndGet();
                    return text.startsWith("match");
                }).maxBytes(64))
                .order(-1)
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .body(String.class)
                .handle((request, pathVariables, body) -> text("matched " + body));
            routes.POST("/bcs/items")
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .body(String.class)
                .handle((request, pathVariables, body) -> text("fallback " + body.length()));
            routes.POST("/bcs/only")
                .where(RouteCondition.bodyText(text -> {
                    EVALUATED.incrementAndGet();
                    return true;
                }).maxBytes(64))
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> text("only"));
            routes.path("/bcs/large", large -> {
                large.where(RouteCondition.bodyBytes(bytes -> EVALUATED.incrementAndGet() > 0).maxBytes(1024 * 1024));
                large.POST("/")
                    .consumes(MediaType.TEXT_PLAIN_TYPE)
                    .handle((request, pathVariables) -> text("large"));
            });
        }

        private static HttpResponse<?> text(String body) {
            return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
