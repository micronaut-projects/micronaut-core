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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.ServerResponseAttributes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A response with the attribute {@link ServerResponseAttributes#SKIP_COMPRESSION} is written as
 * it is, even to a client that accepts a compressed body.
 */
class SkipCompressionTest {
    private static final String SPEC_NAME = "SkipCompressionTest";
    private static final String TEXT = "text ".repeat(1000);

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aResponseThatSkipsCompressionIsWrittenAsItIs() throws IOException {
        String head = head("/skip-compression/skip");
        assertFalse(head.toLowerCase().contains("content-encoding"), head);
        assertTrue(head.toLowerCase().contains("content-length: " + TEXT.length()), head);
    }

    @Test
    void anotherResponseIsStillCompressed() throws IOException {
        String head = head("/skip-compression/compressed");
        assertTrue(head.toLowerCase().contains("content-encoding: gzip"), head);
    }

    private static String head(String path) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            InputStream in = socket.getInputStream();
            StringBuilder head = new StringBuilder();
            while (!head.toString().endsWith("\r\n\r\n")) {
                int c = in.read();
                if (c < 0) {
                    break;
                }
                head.append((char) c);
            }
            in.readAllBytes();
            return head.toString();
        }
    }

    @Controller("/skip-compression")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Get(value = "/skip", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> skip() {
            MutableHttpResponse<String> response = HttpResponse.ok(TEXT);
            response.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, true);
            return response;
        }

        @Get(value = "/compressed", produces = MediaType.TEXT_PLAIN)
        String compressed() {
            return TEXT;
        }
    }
}
