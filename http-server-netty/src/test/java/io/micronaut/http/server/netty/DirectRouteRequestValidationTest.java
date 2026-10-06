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
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The requests a direct route never answers, as the server answers them with an error: a request
 * the decoder failed on, and an invalid request target or query. A route that throws an
 * {@link Error} is answered with {@code 500}, and the connection serves the next request.
 */
class DirectRouteRequestValidationTest {
    private static final String SPEC_NAME = "DirectRouteRequestValidationTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.idle-timeout", "5s",
            "micronaut.server.netty.max-header-size", 1024));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aMalformedHeaderIsAnsweredWith400() throws IOException {
        String response = exchange("GET /validation/greet/x HTTP/1.1\r\nHost: localhost\r\nBad Header: x\r\n\r\n");
        assertTrue(response.startsWith("http/1.1 400 "), response);
    }

    @Test
    void headersLongerThanAllowedAreAnsweredWith413() throws IOException {
        String response = exchange("GET /validation/greet/x HTTP/1.1\r\nHost: localhost\r\nX-Long: " + "x".repeat(2048) + "\r\n\r\n");
        assertTrue(response.startsWith("http/1.1 413 "), response);
    }

    @Test
    void anInvalidRequestTargetIsAnsweredWith400() throws IOException {
        assertTrue(exchange(get("/validation/greet/ok")).endsWith("hello ok"));
        for (String target : new String[] {"/validation/greet/%zz", "/validation/greet/a|b", "/validation/q?mode=%zz"}) {
            String response = exchange(get(target));
            assertTrue(response.startsWith("http/1.1 400 "), target + ": " + response);
        }
        // a method without direct routes: the target is validated by the ordinary path only
        String post = exchange("POST /validation/greet/%zz HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n");
        assertTrue(post.startsWith("http/1.1 400 "), post);
    }

    @Test
    void anAbsoluteFormTargetIsMatchedByItsPath() throws IOException {
        String response = exchange(get("http://localhost/validation/greet/absolute"));
        assertTrue(response.startsWith("http/1.1 200 "), response);
        assertTrue(response.endsWith("hello absolute"), response);
    }

    @Test
    void anErrorOfARouteIsAnsweredWith500AndTheConnectionServesTheNextRequest() throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            for (String failing : new String[] {"/validation/error", "/validation/error-executor"}) {
                out.write(DirectRouteTest.request("GET", failing));
                String error = DirectRouteTest.readResponse(in, false);
                assertTrue(error.startsWith("http/1.1 500 "), failing + ": " + error);
                out.write(DirectRouteTest.request("GET", "/validation/greet/next"));
                String next = DirectRouteTest.readResponse(in, false);
                assertTrue(next.endsWith("hello next"), next);
            }
        }
    }

    private static String get(String target) {
        return "GET " + target + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
    }

    private static String exchange(String request) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return DirectRouteTest.readResponse(socket.getInputStream(), false);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/validation/greet/{name}").respond(direct -> direct.responses()
                .ok("hello " + direct.pathVariables().getString("name"))
                .contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/validation/q").where(RouteCondition.query("mode", "debug")).respond(HttpResponse.ok("debug"));
            routes.GET("/validation/error").respond(direct -> {
                throw new AssertionError("failed");
            });
            routes.GET("/validation/error-executor").executeOn(TaskExecutors.BLOCKING).respond(direct -> {
                throw new AssertionError("failed");
            });
        }
    }
}
