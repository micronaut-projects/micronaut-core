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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Put;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route with a {@code @Body} whose body arrives after the head, that answers with a status
 * handled by an {@code @Error(status = ...)} route, must be answered (the stored late-body flow
 * used to be re-chained onto itself, spinning the event loop).
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class LateBodyStatusRouteTest {
    private static final String SPEC_NAME = "LateBodyStatusRouteTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() throws InterruptedException {
        if (ctx != null) {
            // with the bug the event loop never returns, so a graceful shutdown would never end
            Thread closer = new Thread(ctx::close, "LateBodyStatusRouteTest-close");
            closer.setDaemon(true);
            closer.start();
            closer.join(10_000);
        }
    }

    @Test
    void bodyWithTheHead() throws IOException {
        String body = "{\"firstName\":\"Nobody\"}";
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write((head(body) + "\r\n" + body).getBytes(StandardCharsets.US_ASCII));
            String response = readHead(socket.getInputStream());
            assertTrue(response.startsWith("HTTP/1.1 404"), response);
        }
    }

    @Test
    void lateBody() throws IOException {
        String body = "{\"firstName\":\"Nobody\"}";
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write((head(body) + "Expect: 100-continue\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String interim = readHead(in);
            assertTrue(interim.startsWith("HTTP/1.1 100"), interim);
            out.write(body.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response;
            try {
                response = readHead(in);
            } catch (SocketTimeoutException e) {
                response = "no response within 5s";
            }
            assertTrue(response.startsWith("HTTP/1.1 404"), response);
        }
    }

    private static String head(String body) {
        return "PUT /late-body/people/Nobody HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n";
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b == -1) {
                break;
            }
            bytes.write(b);
            matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    @Controller("/late-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class People {
        @Put(value = "/people/{name}", consumes = MediaType.APPLICATION_JSON)
        HttpResponse<?> update(String name, @Body Map<String, Object> person) {
            return HttpResponse.notFound();
        }

        @Error(status = HttpStatus.NOT_FOUND)
        HttpResponse<String> notFound() {
            return HttpResponse.<String>status(HttpStatus.NOT_FOUND).body("handled");
        }
    }
}
