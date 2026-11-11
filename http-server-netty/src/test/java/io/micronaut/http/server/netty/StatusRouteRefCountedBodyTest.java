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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A status route that is invoked owns its refcounted {@code @Body} argument: the request does not
 * release it when it is disposed.
 */
class StatusRouteRefCountedBodyTest {
    private static final String SPEC_NAME = "StatusRouteRefCountedBodyTest";
    static final List<ByteBuf> KEPT = new CopyOnWriteArrayList<>();

    @Test
    void routeMissStatusRouteKeepsItsByteBufBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String response = post(server.getPort(), "/status-route-body/missing");
            assertTrue(response.startsWith("HTTP/1.1 404"), response);
            assertEquals(1, KEPT.size(), "the status route was not invoked with the body");
            // the request is disposed after the response is written
            Thread.sleep(500);
            ByteBuf kept = KEPT.get(0);
            assertEquals(1, kept.refCnt(), "the request released the body the status route owns");
            kept.release();
        }
    }

    private static String post(int port, String path) throws IOException {
        byte[] body = new byte[64 * 1024];
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" +
                "Content-Type: application/octet-stream\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(response);
            return response.toString(StandardCharsets.ISO_8859_1);
        }
    }

    @Controller("/status-route-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Error(status = HttpStatus.NOT_FOUND, global = true)
        io.micronaut.http.HttpResponse<String> notFound(@Body ByteBuf body) {
            KEPT.add(body);
            return io.micronaut.http.HttpResponse.notFound("missing");
        }
    }
}
