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
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ByteBuf items queued in a streamed response when the client disconnects must be released.
 */
class StreamedByteBufDisconnectTest {
    private static final String SPEC_NAME = "StreamedByteBufDisconnectTest";
    static final List<ByteBuf> ALLOCATED = new CopyOnWriteArrayList<>();
    static volatile CountDownLatch cancelled = new CountDownLatch(1);

    @Test
    void queuedByteBufsAreReleasedOnDisconnect() throws Exception {
        // a small send buffer, so the response cannot be written in full before the client
        // disconnects: on Linux loopback the default send buffer is sized from the 64KB MSS (about
        // 2.6MB), which takes the whole response, and the publisher completes instead of being cancelled
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.netty.childOptions.soSndbuf", 8192))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket s = new Socket("127.0.0.1", server.getPort())) {
                s.setReceiveBufferSize(4096);
                OutputStream out = s.getOutputStream();
                out.write("GET /streamed-bytebuf-disconnect/bufs HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                InputStream in = s.getInputStream();
                in.readNBytes(1024);
                Thread.sleep(300);
            }
            assertTrue(cancelled.await(10, TimeUnit.SECONDS), "publisher cancelled");
            Thread.sleep(1500);
            long still = ALLOCATED.stream().filter(b -> b.refCnt() > 0).count();
            long bytes = ALLOCATED.stream().filter(b -> b.refCnt() > 0).mapToLong(ByteBuf::capacity).sum();
            assertEquals(0, still, "ByteBufs still referenced after disconnect (allocated=" + ALLOCATED.size() + ", retained bytes=" + bytes + ")");
        }
    }

    @Controller("/streamed-bytebuf-disconnect")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ctrl {
        @Get(value = "/bufs", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<ByteBuf> bufs() {
            return Flux.range(0, 20)
                .map(i -> {
                    ByteBuf b = ByteBufAllocator.DEFAULT.buffer(128 * 1024);
                    b.writeZero(128 * 1024);
                    ALLOCATED.add(b);
                    return b;
                })
                .publishOn(Schedulers.boundedElastic(), 8)
                .doOnCancel(() -> cancelled.countDown());
        }
    }
}
