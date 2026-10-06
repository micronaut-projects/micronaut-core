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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.direct.DirectRouteBuilder;
import io.micronaut.web.router.direct.HttpDirectRoutes;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plaintext and JSON tests of the TechEmpower Framework Benchmarks written with direct
 * routes: each response is composed for its request by a supplier, which sets the {@code Server}
 * and {@code Date} headers, as the server adds none to a direct response; only the body text of
 * the plaintext response is shared, and the JSON object is created and serialized for each
 * request. Pipelined requests, mixed with ordinary routes, are answered in order on one
 * connection, and a thousand requests on one connection leak nothing (the Netty leak detector of
 * the tests runs in paranoid mode).
 */
class DirectRouteBenchmarkTest {
    private static final String SPEC_NAME = "DirectRouteBenchmarkTest";
    private static final byte[] HELLO = "Hello, World!".getBytes(StandardCharsets.US_ASCII);

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static BenchmarkRoutes routes;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.idle-timeout", "10s"));
        server = ctx.getBean(EmbeddedServer.class).start();
        routes = ctx.getBean(BenchmarkRoutes.class);
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void plaintext() throws IOException {
        try (Connection connection = new Connection()) {
            Response response = connection.exchange("GET", "/plaintext");
            assertEquals(200, response.status);
            assertEquals("Hello, World!", response.body);
            assertEquals("text/plain", response.header("content-type"));
            assertEquals("13", response.header("content-length"));
            assertEquals("Micronaut", response.header("server"));
            assertValidDate(response.header("date"));
            assertFalse(response.headers.containsKey("content-encoding"));
            assertFalse(response.headers.containsKey("transfer-encoding"));
        }
    }

    @Test
    void json() throws IOException {
        try (Connection connection = new Connection()) {
            Response response = connection.exchange("GET", "/json");
            assertEquals(200, response.status);
            assertEquals("{\"message\":\"Hello, World!\"}", response.body);
            assertEquals("application/json", response.header("content-type"));
            assertEquals("27", response.header("content-length"));
            assertEquals("Micronaut", response.header("server"));
            assertValidDate(response.header("date"));
            assertFalse(response.headers.containsKey("content-encoding"));
        }
    }

    @Test
    void theSupplierComposesEachResponse() throws IOException {
        int plaintext = routes.plaintext.get();
        int json = routes.json.get();
        try (Connection connection = new Connection()) {
            for (int i = 0; i < 5; i++) {
                connection.exchange("GET", "/plaintext");
                connection.exchange("GET", "/json");
            }
        }
        assertEquals(plaintext + 5, routes.plaintext.get());
        assertEquals(json + 5, routes.json.get());
    }

    @Test
    void theDateChangesAcrossSeconds() throws Exception {
        try (Connection connection = new Connection()) {
            String first = connection.exchange("GET", "/plaintext").header("date");
            String second = first;
            long deadline = System.currentTimeMillis() + 5000;
            while (second.equals(first) && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
                second = connection.exchange("GET", "/plaintext").header("date");
            }
            assertNotEquals(first, second, "the Date header is composed for each request, not pre-rendered");
            assertValidDate(second);
        }
    }

    @Test
    void pipelinedRequestsAreAnsweredInOrder() throws IOException {
        try (Connection connection = new Connection()) {
            ByteArrayOutputStream requests = new ByteArrayOutputStream();
            for (int i = 0; i < 16; i++) {
                requests.write(DirectRouteTest.request("GET", "/plaintext"));
            }
            connection.out.write(requests.toByteArray());
            for (int i = 0; i < 16; i++) {
                Response response = connection.read(false);
                assertEquals(200, response.status);
                assertEquals("Hello, World!", response.body);
                assertEquals("13", response.header("content-length"));
            }

            // direct and ordinary routes mixed, in one write
            List<String> paths = List.of("/plaintext", "/ordinary/1", "/json", "/ordinary/2", "/plaintext", "/json", "/ordinary/3", "/plaintext");
            requests.reset();
            for (String path : paths) {
                requests.write(DirectRouteTest.request("GET", path));
            }
            connection.out.write(requests.toByteArray());
            List<String> bodies = new ArrayList<>();
            for (int i = 0; i < paths.size(); i++) {
                Response response = connection.read(false);
                assertEquals(200, response.status);
                assertEquals(String.valueOf(response.body.getBytes(StandardCharsets.UTF_8).length), response.header("content-length"));
                bodies.add(response.body);
            }
            assertEquals(List.of("Hello, World!", "ordinary 1", "{\"message\":\"Hello, World!\"}", "ordinary 2",
                "Hello, World!", "{\"message\":\"Hello, World!\"}", "ordinary 3", "Hello, World!"), bodies);
        }
    }

    @Test
    void aThousandRequestsOnOneConnection() throws IOException {
        try (Connection connection = new Connection()) {
            for (int i = 0; i < 1000; i++) {
                Response response = connection.exchange("GET", i % 2 == 0 ? "/plaintext" : "/json");
                assertEquals(200, response.status);
            }
            // the connection is still open
            assertEquals("Hello, World!", connection.exchange("GET", "/plaintext").body);
        }
    }

    private static void assertValidDate(String date) {
        ZonedDateTime parsed = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME);
        long age = Math.abs(System.currentTimeMillis() - parsed.toInstant().toEpochMilli());
        assertTrue(age < 10_000, date);
    }

    /**
     * A response read from the socket.
     */
    private record Response(int status, Map<String, String> headers, String body) {
        String header(String name) {
            return headers.get(name);
        }
    }

    /**
     * A keep-alive connection to the server.
     */
    private static final class Connection implements AutoCloseable {
        private final Socket socket;
        private final OutputStream out;
        private final InputStream in;

        Connection() throws IOException {
            socket = new Socket(server.getHost(), server.getPort());
            socket.setSoTimeout(10_000);
            out = socket.getOutputStream();
            in = socket.getInputStream();
        }

        Response exchange(String method, String path) throws IOException {
            out.write(DirectRouteTest.request(method, path));
            return read("HEAD".equals(method));
        }

        /**
         * Read one response: its head, then as many bytes as its content-length, none for a
         * HEAD response.
         */
        Response read(boolean head) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int matched = 0;
            while (matched < 4) {
                int b = in.read();
                if (b < 0) {
                    throw new IOException("Connection closed after " + bytes.toString(StandardCharsets.US_ASCII));
                }
                bytes.write(b);
                matched = b == (matched % 2 == 0 ? '\r' : '\n') ? matched + 1 : (b == '\r' ? 1 : 0);
            }
            String[] lines = bytes.toString(StandardCharsets.US_ASCII).split("\r\n");
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                String previous = headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
                assertEquals(null, previous, "a header is written once: " + lines[i]);
            }
            int status = Integer.parseInt(lines[0].split(" ")[1]);
            String length = headers.get("content-length");
            byte[] body = head || length == null ? new byte[0] : in.readNBytes(Integer.parseInt(length));
            return new Response(status, headers, new String(body, StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Introspected
    record Message(String message) {
    }

    /**
     * @return The Date header value, composed by the route for each request
     */
    private static String date() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BenchmarkRoutes implements HttpRoutes, HttpDirectRoutes {
        final AtomicInteger plaintext = new AtomicInteger();
        final AtomicInteger json = new AtomicInteger();

        @Override
        public void routes(DirectRouteBuilder builder) {
            builder.GET("/plaintext").respond(direct -> {
                plaintext.incrementAndGet();
                return direct.responses().ok(HELLO)
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .header(HttpHeaders.SERVER, "Micronaut")
                    .header(HttpHeaders.DATE, date());
            });
            builder.GET("/json").respond(direct -> {
                json.incrementAndGet();
                return direct.responses().ok(new Message("Hello, World!"))
                    .header(HttpHeaders.SERVER, "Micronaut")
                    .header(HttpHeaders.DATE, date());
            });
        }

        @Override
        public void routes(HttpRouteBuilder builder) {
            builder.GET("/ordinary/{n}", (request, pathVariables) ->
                HttpResponse.ok("ordinary " + pathVariables.getString("n")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
