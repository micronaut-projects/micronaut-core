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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refcounted {@code @Body} argument of a route that is never invoked (another argument fails
 * to bind) must be released with the request.
 */
class UnboundRouteRefCountedBodyTest {
    private static final String SPEC_NAME = "UnboundRouteRefCountedBodyTest";
    static final List<ReferenceCounted> SEEN = new CopyOnWriteArrayList<>();
    static final AtomicInteger INVOKED = new AtomicInteger();

    @Test
    void unboundRouteReleasesByteBufBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            String rejected = post(server.getPort(), "/unbound-body/plain");
            assertTrue(rejected.startsWith("HTTP/1.1 400"), rejected);
            String invoked = post(server.getPort(), "/unbound-body/plain?q=1");
            assertTrue(invoked.startsWith("HTTP/1.1 200"), invoked);
            assertEquals(1, INVOKED.get());
            assertEquals(2, SEEN.size(), "the response filter saw the bound ByteBuf bodies");
            // the request is released after the response is written
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (SEEN.get(0).refCnt() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(0, SEEN.get(0).refCnt(), "ByteBuf body of the route that was not invoked is still referenced");
            assertEquals(0, SEEN.get(1).refCnt(), "ByteBuf body of the invoked route is still referenced");
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

    @ServerFilter("/unbound-body/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Capture {
        @ResponseFilter
        void response(HttpRequest<?> request) {
            request.getBody().ifPresent(b -> {
                if (b instanceof ReferenceCounted rc) {
                    SEEN.add(rc);
                }
            });
        }
    }

    @Controller("/unbound-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Post(value = "/plain", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        String plain(@Body ByteBuf body, @QueryValue String q) {
            INVOKED.incrementAndGet();
            body.release();
            return "ok";
        }
    }
}
