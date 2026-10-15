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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body conditions of handler routes against the buffer of the server: a body larger than
 * the server may buffer cannot be kept for another route, so it fails with 413 when it is read
 * for the conditions, and a body that does not meet the conditions of its routes is not found,
 * even when a static resource is on the path.
 */
class HandlerRouteBodyConditionsServerLimitTest {
    private static final String SPEC_NAME = "HandlerRouteBodyConditionsServerLimitTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", "1KB",
            "micronaut.router.static-resources.default.paths", List.of("classpath:public"),
            "micronaut.router.static-resources.default.mapping", "/static/**"
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
    void aChunkedBodyLargerThanTheServerBufferFailsWith413() throws Exception {
        String large = "match" + "x".repeat(4000);
        assertEquals("413", chunked("/cap/items", large.substring(0, 500), large.substring(500)));
        // the server keeps serving
        assertEquals("matched", fixed("/cap/items", "match"));
    }

    @Test
    void aBodyWhoseLengthIsLargerThanTheServerBufferIsNotReadAndGoesToTheNextRoute() throws Exception {
        assertEquals("fallback", fixed("/cap/items", "match" + "x".repeat(4000)));
    }

    @Test
    void aChunkedBodyLargerThanTheLimitOfTheConditionsWithinTheServerBufferGoesToTheNextRoute() throws Exception {
        String body = "match" + "x".repeat(595);
        assertEquals("fallback 600", chunked("/cap/limited", body.substring(0, 40), body.substring(40)));
    }

    @Test
    void aBodyThatMeetsNoConditionIsNotFoundEvenWithAStaticResourceOnThePath() throws Exception {
        assertEquals("404", fixed("/static/index.html", "other"));
        assertEquals("matched static", fixed("/static/index.html", "static"));
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

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/cap/items")
                .where(RouteCondition.bodyText(text -> text.startsWith("match")))
                .order(-1)
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> text("matched"));
            routes.POST("/cap/items")
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> text("fallback"));
            routes.POST("/cap/limited")
                .where(RouteCondition.bodyText(text -> true).maxBytes(64))
                .order(-1)
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> text("matched"));
            routes.POST("/cap/limited")
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .body(String.class)
                .handle((request, pathVariables, body) -> text("fallback " + body.length()));
            routes.POST("/static/index.html")
                .where(RouteCondition.bodyText("static"::equals))
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .handle((request, pathVariables) -> text("matched static"));
        }

        private static HttpResponse<?> text(String body) {
            return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
