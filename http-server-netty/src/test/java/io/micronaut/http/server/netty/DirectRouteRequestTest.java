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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.direct.DirectRequest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The function of a direct route reads the Netty request it answers: its method, its headers,
 * its query and the address of its peer, on the event loop and on an executor.
 */
class DirectRouteRequestTest {
    private static final String SPEC_NAME = "DirectRouteRequestTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aRouteOfSeveralMethodsReadsTheMethod() throws IOException {
        assertTrue(exchange("GET /request/method HTTP/1.1\r\nHost: localhost\r\n\r\n", false).endsWith("\r\n\r\nGET"));
        assertTrue(exchange("POST /request/method HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n", false).endsWith("\r\n\r\nPOST"));
        // the implicit HEAD route of the GET route: the length of the body it composed for HEAD
        String head = exchange("HEAD /request/method HTTP/1.1\r\nHost: localhost\r\n\r\n", true);
        assertTrue(head.startsWith("http/1.1 200 "), head);
        assertTrue(head.contains("content-length: 4\r\n"), head);
    }

    @Test
    void aConditionalGetIsAnsweredWith304() throws IOException {
        String notModified = exchange("GET /request/etag HTTP/1.1\r\nHost: localhost\r\nIf-None-Match: \"v1\"\r\n\r\n", false);
        assertTrue(notModified.startsWith("http/1.1 304 "), notModified);
        assertFalse(notModified.contains("content-length"), notModified);
        String modified = exchange("GET /request/etag HTTP/1.1\r\nHost: localhost\r\nIf-None-Match: \"v0\"\r\n\r\n", false);
        assertTrue(modified.startsWith("http/1.1 200 "), modified);
        assertTrue(modified.contains("etag: \"v1\"\r\n"), modified);
        assertTrue(modified.endsWith("body"), modified);
    }

    @Test
    void theFunctionReadsTheHeadersTheQueryAndThePeer() throws IOException {
        String echo = exchange("GET /request/echo?q=a&q=b%20c HTTP/1.1\r\nHost: localhost\r\nX-Echo: one\r\nX-Echo: two\r\n\r\n", false);
        assertTrue(echo.endsWith("echo=[one, two] q=[a, b c] loopback=true"), echo);
        String none = exchange("GET /request/echo HTTP/1.1\r\nHost: localhost\r\n\r\n", false);
        assertTrue(none.endsWith("echo=[] q=[] loopback=true"), none);
    }

    @Test
    void aFunctionOnAnExecutorReadsTheRequest() throws IOException {
        String response = exchange("GET /request/executor?q=x HTTP/1.1\r\nHost: localhost\r\nX-Echo: e\r\n\r\n", false);
        assertTrue(response.endsWith("q=x echo=e"), response);
    }

    private static String exchange(String request, boolean headResponse) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return DirectRouteTest.readResponse(socket.getInputStream(), headResponse);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.route(EnumSet.of(HttpMethod.GET, HttpMethod.POST), "/request/method")
                .respond(direct -> HttpResponse.ok(direct.request().method().name()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/request/etag").respond(direct -> "\"v1\"".equals(direct.request().header(HttpHeaders.IF_NONE_MATCH))
                ? HttpResponse.notModified()
                : HttpResponse.ok("body").header(HttpHeaders.ETAG, "\"v1\"").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/request/echo").respond(direct -> {
                DirectRequest request = direct.request();
                InetSocketAddress peer = request.peerAddress();
                return HttpResponse.ok("echo=" + request.headers("X-Echo") + " q=" + request.queryParameters("q")
                    + " loopback=" + (peer != null && peer.getAddress().isLoopbackAddress()))
                    .contentType(MediaType.TEXT_PLAIN_TYPE);
            });
            routes.GET("/request/executor").executeOn(TaskExecutors.BLOCKING).respond(direct ->
                HttpResponse.ok("q=" + direct.request().queryParameter("q") + " echo=" + direct.request().header("X-Echo"))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
