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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.buffer.Unpooled;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the Netty server frames the response of a direct route, and the headers it adds: a buffer
 * given as a value, the {@code Content-Length} of a {@code HEAD} response, a {@code HEAD} route
 * that declines, and the configured {@code Date} and {@code Server} headers.
 */
class DirectRouteFramingTest {
    private static final String SPEC_NAME = "DirectRouteFramingTest";
    private static final String ROUTE_DATE = "Thu, 01 Jan 2026 00:00:00 GMT";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.idle-timeout", "5s",
            "micronaut.server.server-header", "framing-test"));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aBufferGivenAsAValueIsWrittenForEveryRequest() {
        // writing a buffer releases it: the route keeps a copy of its bytes
        for (int i = 0; i < 3; i++) {
            assertEquals("netty buffer", client.toBlocking().retrieve(HttpRequest.GET("/framing/buf")));
            assertEquals("micronaut buffer", client.toBlocking().retrieve(HttpRequest.GET("/framing/micronaut-buf")));
        }
    }

    @Test
    void aHeadResponseHasTheContentLengthOfItsRouteOrOfItsBody() throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // a HEAD route without a body declares the length of the GET response
            out.write(DirectRouteTest.request("HEAD", "/framing/head-length"));
            String declared = DirectRouteTest.readResponse(in, true);
            assertTrue(declared.startsWith("http/1.1 200 ok\r\n"), declared);
            assertTrue(declared.contains("content-length: 42\r\n"), declared);

            // the implicit HEAD route of a GET route: the length of its body
            out.write(DirectRouteTest.request("HEAD", "/framing/text"));
            String implicit = DirectRouteTest.readResponse(in, true);
            assertTrue(implicit.contains("content-length: 4\r\n"), implicit);

            // a status without a body has no length
            out.write(DirectRouteTest.request("HEAD", "/framing/no-content"));
            String noContent = DirectRouteTest.readResponse(in, true);
            assertTrue(noContent.startsWith("http/1.1 204 no content\r\n"), noContent);
            assertFalse(noContent.contains("content-length"), noContent);

            // the connection is still usable
            out.write(DirectRouteTest.request("GET", "/framing/text"));
            assertTrue(DirectRouteTest.readResponse(in, false).endsWith("text"));
        }
    }

    @Test
    void aHeadRouteThatDeclinesContinuesToTheOrdinaryRouteAsynchronousOrNot() throws IOException {
        for (String path : new String[] {"/framing/declined-sync", "/framing/declined-async"}) {
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(DirectRouteTest.request("HEAD", path));
                String head = DirectRouteTest.readResponse(socket.getInputStream(), true);
                assertTrue(head.startsWith("http/1.1 200 ok\r\n"), path + ": " + head);
                // not the direct GET route of the path
                assertTrue(head.contains("x-answered: ordinary\r\n"), path + ": " + head);
            }
            // which answers the GET requests
            HttpResponse<String> get = client.toBlocking().exchange(HttpRequest.GET(path), String.class);
            assertEquals("direct", get.getHeaders().get("X-Answered"));
        }
    }

    @Test
    void theConfiguredHeadersAreAddedUnlessTheRouteSetsThem() {
        HttpResponse<String> plain = client.toBlocking().exchange(HttpRequest.GET("/framing/text"), String.class);
        assertNotNull(plain.getHeaders().get(HttpHeaders.DATE));
        assertEquals("framing-test", plain.getHeaders().get(HttpHeaders.SERVER));

        HttpResponse<String> own = client.toBlocking().exchange(HttpRequest.GET("/framing/own-headers"), String.class);
        assertEquals(List.of(ROUTE_DATE), own.getHeaders().getAll(HttpHeaders.DATE));
        assertEquals(List.of("own-server"), own.getHeaders().getAll(HttpHeaders.SERVER));
    }

    @Test
    void theDateHeaderIsLeftOutWhenTheServerIsConfiguredSo() {
        try (EmbeddedServer noDate = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "spec.name", SPEC_NAME,
                "micronaut.server.date-header", false));
             HttpClient noDateClient = noDate.getApplicationContext().createBean(HttpClient.class, noDate.getURL())) {
            HttpResponse<String> response = noDateClient.toBlocking().exchange(HttpRequest.GET("/framing/text"), String.class);
            assertEquals("text", response.body());
            assertNull(response.getHeaders().get(HttpHeaders.DATE));
            assertNull(response.getHeaders().get(HttpHeaders.SERVER));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes, HttpRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/framing/buf", HttpResponse.ok(Unpooled.copiedBuffer("netty buffer", StandardCharsets.UTF_8)));
            routes.GET("/framing/micronaut-buf", HttpResponse.ok(NettyByteBufferFactory.DEFAULT.copiedBuffer(
                "micronaut buffer".getBytes(StandardCharsets.UTF_8))));
            routes.GET("/framing/text", HttpResponse.ok("text"));
            routes.HEAD("/framing/head-length", HttpResponse.ok().header(HttpHeaders.CONTENT_LENGTH, "42"));
            routes.GET("/framing/no-content", HttpResponse.noContent());
            routes.GET("/framing/own-headers", HttpResponse.ok("own")
                .header(HttpHeaders.DATE, ROUTE_DATE)
                .header(HttpHeaders.SERVER, "own-server"));

            routes.HEAD("/framing/declined-sync").respond(direct -> null);
            routes.GET("/framing/declined-sync", HttpResponse.ok("direct").header("X-Answered", "direct"));
            routes.HEAD("/framing/declined-async").respondAsync(direct -> CompletableFuture.completedFuture(null));
            routes.GET("/framing/declined-async", HttpResponse.ok("direct").header("X-Answered", "direct"));
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            // their implicit HEAD routes answer the HEAD requests the direct HEAD routes decline
            routes.GET("/framing/declined-sync").respond(HttpResponse.ok("ordinary").header("X-Answered", "ordinary"));
            routes.GET("/framing/declined-async").respond(HttpResponse.ok("ordinary").header("X-Answered", "ordinary"));
        }
    }
}
