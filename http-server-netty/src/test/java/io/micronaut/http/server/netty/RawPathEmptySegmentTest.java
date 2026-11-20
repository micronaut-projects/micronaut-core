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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Empty path segments of the request target reach the application unchanged.
 */
class RawPathEmptySegmentTest {
    private static final String SPEC_NAME = "RawPathEmptySegmentTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        ctx.close();
    }

    @Test
    void doubleSlashIsKept() throws Exception {
        assertEquals("/catch/a//b|/catch/a//b", body(get("/catch/a//b")));
    }

    @Test
    void matrixParameterWithEmptySegmentIsKept() throws Exception {
        assertEquals("/catch/a;x=1//b;y=2|/catch/a;x=1//b;y=2", body(get("/catch/a;x=1//b;y=2?q=1")));
    }

    @Test
    void routeMatchingIsUnchanged() throws Exception {
        assertEquals("plain", body(get("/plain/x")));
        assertEquals("plain", body(get("/plain/x/")));
    }

    @Test
    void duplicateSlashesAreNotMergedForMatching() throws Exception {
        assertTrue(get("/plain//x").startsWith("HTTP/1.1 404 "));
        assertTrue(get("//plain/x").startsWith("HTTP/1.1 404 "));
    }

    @Test
    void clientWithContextPathKeepsEmptySegments() {
        HttpClient client = ctx.getBean(ClientHolder.class).client;
        assertEquals("/catch/a//b|/catch/a//b", client.toBlocking().retrieve("/catch/a//b"));
        assertEquals("/catch/a;x=1//b;y=2|/catch/a;x=1//b;y=2", client.toBlocking().retrieve("/catch/a;x=1//b;y=2?q=1"));
    }

    private static String body(String response) {
        assertTrue(response.startsWith("HTTP/1.1 200 "), response);
        return response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    private static String get(String target) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            socket.getOutputStream().write(("GET " + target + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(response);
            return response.toString(StandardCharsets.UTF_8);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClientHolder {
        @Inject
        @Client("/")
        HttpClient client;
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PathController {
        @Get(value = "/catch/{+rest}", produces = MediaType.TEXT_PLAIN)
        String echo(HttpRequest<?> request, String rest) {
            return request.getPath() + "|" + request.getUri().getRawPath();
        }

        @Get(value = "/plain/x", produces = MediaType.TEXT_PLAIN)
        String plain() {
            return "plain";
        }
    }
}
