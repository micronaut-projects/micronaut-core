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
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.context.event.HttpRequestReceivedEvent;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.simple.SimpleHttpResponseFactory;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct routes on the Netty server: answered in the pipeline from the Netty request, before a
 * {@link io.micronaut.http.HttpRequest} is created, so no filter, request event or request
 * context sees them, over HTTP/1.1 with keep-alive and {@code HEAD}.
 */
class DirectRouteTest {
    private static final String SPEC_NAME = "DirectRouteTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;
    private static Counters counters;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            // a test that leaves a connection open must not wait for the default idle timeout
            "micronaut.server.idle-timeout", "5s",
            "micronaut.server.server-header", "micronaut-test"));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = ctx.createBean(HttpClient.class, server.getURL());
        counters = ctx.getBean(Counters.class);
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

    @BeforeEach
    void reset() {
        counters.reset();
    }

    @Test
    void aDirectRouteIsAnsweredWithoutFiltersRequestEventsOrARequest() {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/direct/health"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("UP", response.body());
            // the headers of the response, its length, and the headers the server is configured to add: no Content-Type
            assertNull(response.getHeaders().get("Content-Type"));
            assertNotNull(response.getHeaders().get("Date"));
            assertEquals("micronaut-test", response.getHeaders().get("Server"));
            assertEquals("2", response.getHeaders().get("Content-Length"));
        }
        HttpResponse<String> computed = client.toBlocking().exchange(HttpRequest.GET("/direct/computed"), String.class);
        assertEquals("computed", computed.body());
        // neither the filter bean, nor the server filter of the routes, nor the request events
        assertEquals(0, counters.filterBean.get());
        assertEquals(0, counters.serverFilter.get());
        assertEquals(0, counters.received.get());
        assertEquals(0, counters.terminated.get());
        // the supplier ran outside of any request context: no HttpRequest was created
        assertTrue(counters.supplierCalled.get());
        assertFalse(counters.supplierSawRequest.get());
    }

    @Test
    void theFunctionBuildsTheResponseOfTheServerRuntime() {
        assertEquals("computed", client.toBlocking().retrieve(HttpRequest.GET("/direct/computed")));
        assertTrue(counters.nettyFactory.get());
        assertTrue(counters.nettyResponse.get());
    }

    @Test
    void aResponseOfAnotherImplementationIsConverted() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/direct/foreign"), String.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertEquals("foreign", response.body());
        assertEquals("yes", response.getHeaders().get("X-Foreign"));
        assertEquals("7", response.getHeaders().get("Content-Length"));
        assertEquals(0, counters.filterBean.get());
    }

    @Test
    void anOrdinaryRouteIsFilteredAsUsual() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/direct/ordinary"), String.class);
        assertEquals("ordinary", response.body());
        // the server completes an ordinary response
        assertNotNull(response.getHeaders().get("Date"));
        assertEquals("micronaut-test", response.getHeaders().get("Server"));
        assertEquals(1, counters.filterBean.get());
        assertEquals(1, counters.serverFilter.get());
        assertEquals(1, counters.received.get());
    }

    @Test
    void aRequestNoDirectRouteMatchesContinuesToTheRouter() {
        // the direct route of /direct/guarded needs the header
        assertEquals("guarded ordinary", client.toBlocking().retrieve(HttpRequest.GET("/direct/guarded")));
        assertEquals("guarded direct", client.toBlocking().retrieve(HttpRequest.GET("/direct/guarded").header("X-Direct", "yes")));
        assertEquals(1, counters.filterBean.get());
    }

    @Test
    void theMostSpecificDirectRouteAndThePathVariables() {
        assertEquals("special", client.toBlocking().retrieve(HttpRequest.GET("/direct/files/special")));
        assertEquals("file report.txt", client.toBlocking().retrieve(HttpRequest.GET("/direct/files/report.txt")));
        assertEquals(0, counters.filterBean.get());
    }

    @Test
    void aBlockListAnswersFromThePeerAddress() {
        HttpClientResponseException forbidden = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/direct/admin")));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
        assertEquals(0, counters.filterBean.get());
    }

    @Test
    void aDirectRouteOfAnotherMethodDiscardsTheBody() {
        HttpClientResponseException gone = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.POST("/direct/legacy", "x".repeat(100_000)).contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals(HttpStatus.GONE, gone.getStatus());
        // the connection is still usable
        assertEquals("UP", client.toBlocking().retrieve(HttpRequest.GET("/direct/health")));
    }

    @Test
    void headAndKeepAliveOnOneConnection() throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(request("GET", "/direct/health"));
            String get = readResponse(in, false);
            assertTrue(get.startsWith("http/1.1 200 ok\r\n"), get);
            assertTrue(get.contains("content-length: 2\r\n"), get);
            assertTrue(get.endsWith("\r\n\r\nUP"), get);

            out.write(request("HEAD", "/direct/health"));
            String head = readResponse(in, true);
            assertTrue(head.startsWith("http/1.1 200 ok\r\n"), head);
            // the length of the GET response, without the body
            assertTrue(head.contains("content-length: 2\r\n"), head);
            assertTrue(head.endsWith("\r\n\r\n"), head);

            out.write(request("GET", "/direct/ordinary"));
            String ordinary = readResponse(in, false);
            assertTrue(ordinary.startsWith("http/1.1 200 ok\r\n"), ordinary);
            assertTrue(ordinary.endsWith("ordinary"), ordinary);

            // pipelined: both are answered in order
            byte[] first = request("GET", "/direct/files/a");
            byte[] second = request("GET", "/direct/files/b");
            byte[] both = new byte[first.length + second.length];
            System.arraycopy(first, 0, both, 0, first.length);
            System.arraycopy(second, 0, both, first.length, second.length);
            out.write(both);
            assertTrue(readResponse(in, false).endsWith("file a"));
            assertTrue(readResponse(in, false).endsWith("file b"));
        }
        // only the ordinary route received a request
        assertEquals(1, counters.received.get());
    }

    static byte[] request(String method, String path) {
        return (method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Read one response: its head, with lower case header names, then as many bytes as its
     * content-length, none for a HEAD response, whose head has the length of the GET response.
     */
    static String readResponse(InputStream in, boolean headResponse) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("Connection closed after " + head);
            }
            head.write(b);
        }
        String text = head.toString(StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        int length = 0;
        for (String line : text.split("\r\n")) {
            if (line.startsWith("content-length:")) {
                length = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        if (headResponse) {
            return text;
        }
        byte[] body = in.readNBytes(length);
        return text + new String(body, StandardCharsets.UTF_8);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Counters implements ApplicationEventListener<HttpRequestReceivedEvent> {
        final AtomicInteger filterBean = new AtomicInteger();
        final AtomicInteger serverFilter = new AtomicInteger();
        final AtomicInteger received = new AtomicInteger();
        final AtomicInteger terminated = new AtomicInteger();
        final AtomicBoolean supplierCalled = new AtomicBoolean();
        final AtomicBoolean supplierSawRequest = new AtomicBoolean();
        final AtomicBoolean nettyFactory = new AtomicBoolean();
        final AtomicBoolean nettyResponse = new AtomicBoolean();

        void reset() {
            filterBean.set(0);
            serverFilter.set(0);
            received.set(0);
            terminated.set(0);
            supplierCalled.set(false);
            supplierSawRequest.set(false);
        }

        @Override
        public void onApplicationEvent(HttpRequestReceivedEvent event) {
            received.incrementAndGet();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TerminatedListener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        private final Counters counters;

        TerminatedListener(Counters counters) {
            this.counters = counters;
        }

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent event) {
            counters.terminated.incrementAndGet();
        }
    }

    @ServerFilter("/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CountingFilter {
        private final Counters counters;

        CountingFilter(Counters counters) {
            this.counters = counters;
        }

        @RequestFilter
        void count() {
            counters.filterBean.incrementAndGet();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        private final Counters counters;

        Routes(Counters counters) {
            this.counters = counters;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.serverFilter("/**").before(request -> {
                counters.serverFilter.incrementAndGet();
            });
            routes.GET("/direct/ordinary", (request, pathVariables) -> text("ordinary"));
            routes.GET("/direct/guarded", (request, pathVariables) -> text("guarded ordinary"));
            routes.GET("/direct/admin", (request, pathVariables) -> text("admin"));
        }

        private static HttpResponse<?> text(String body) {
            return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DirectRoutes implements HttpDirectRoutes {
        private final Counters counters;

        DirectRoutes(Counters counters) {
            this.counters = counters;
        }

        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/direct/health", HttpResponse.ok("UP"));
            routes.GET("/direct/computed").respond(direct -> {
                counters.supplierCalled.set(true);
                counters.supplierSawRequest.set(ServerRequestContext.currentRequest().isPresent());
                MutableHttpResponse<String> response = HttpResponse.ok("computed");
                // the static factory of HttpResponse is the one of the server
                counters.nettyFactory.set(HttpResponseFactory.INSTANCE instanceof NettyHttpResponseFactory);
                counters.nettyResponse.set(response instanceof NettyMutableHttpResponse<?>);
                return response;
            });
            // a response of another implementation than the one of the server
            routes.GET("/direct/foreign").respond(direct -> new SimpleHttpResponseFactory().status(HttpStatus.ACCEPTED, "Accepted")
                .body("foreign")
                .header("X-Foreign", "yes"));

            routes.GET("/direct/guarded").where(RouteCondition.header("X-Direct")).respond(HttpResponse.ok("guarded direct"));

            routes.GET("/direct/files/{name}").respond(direct -> HttpResponse.ok("file " + direct.pathVariables().getString("name")));
            routes.GET("/direct/files/special", HttpResponse.ok("special"));

            // the test client connects from the loopback address
            routes.GET("/direct/admin")
                .where(RouteCondition.peerAddress("127.0.0.0/8", "::1"))
                .respond(HttpResponse.status(HttpStatus.FORBIDDEN));

            routes.POST("/direct/legacy", HttpResponse.status(HttpStatus.GONE));
        }
    }
}
