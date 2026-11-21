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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A HEAD request to a GET route that returns a {@link ByteBody} must release the body the route
 * produced, as the GET request does.
 */
class HeadByteBodyReleaseTest {
    private static final String SPEC_NAME = "HeadByteBodyReleaseTest";
    static final ConcurrentLinkedQueue<ByteBuf> BUFFERS = new ConcurrentLinkedQueue<>();

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
    void getReleasesTheBody() throws Exception {
        assertReleased("GET");
    }

    @Test
    void headReleasesTheBody() throws Exception {
        assertReleased("HEAD");
    }

    private static void assertReleased(String method) throws Exception {
        BUFFERS.clear();
        String response = exchange(method + " /head-byte-body HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        ByteBuf buf = BUFFERS.poll();
        assertTrue(buf != null, "route did not run");
        long deadline = System.currentTimeMillis() + 2000;
        while (buf.refCnt() != 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        int refCnt = buf.refCnt();
        if (refCnt != 0) {
            buf.release(refCnt);
        }
        assertEquals(0, refCnt, method + " leaked the ByteBody returned by the route");
    }

    private static String exchange(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Export {
        private final NettyByteBodyFactory factory = new NettyByteBodyFactory(new EmbeddedChannel());

        @Get(value = "/head-byte-body", produces = MediaType.TEXT_PLAIN)
        ByteBody export() {
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes("a,b,c\n".repeat(100).getBytes(StandardCharsets.UTF_8));
            BUFFERS.add(buf);
            return factory.adapt(buf);
        }
    }
}
