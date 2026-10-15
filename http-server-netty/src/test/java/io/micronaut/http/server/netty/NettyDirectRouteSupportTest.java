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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * How the Netty server manages the bodies of direct routes: a shared buffer is copied once, and
 * the buffer of a response that is never written is released.
 */
class NettyDirectRouteSupportTest {

    private final NettyDirectRouteSupport support = new NettyDirectRouteSupport();

    @Test
    void aSharedBufferIsCopiedAndReleased() {
        ByteBuf buf = Unpooled.copiedBuffer("shared", StandardCharsets.UTF_8);
        assertArrayEquals("shared".getBytes(StandardCharsets.UTF_8), (byte[]) support.shareableBody(buf));
        assertEquals(0, buf.refCnt());
        // any other body is shared as it is
        Object record = new Object();
        assertSame(record, support.shareableBody(record));
    }

    @Test
    void theBufferOfADiscardedResponseIsReleased() {
        ByteBuf buf = Unpooled.copiedBuffer("late", StandardCharsets.UTF_8);
        support.discard(HttpResponse.ok(buf));
        assertEquals(0, buf.refCnt());

        ByteBuffer<ByteBuf> buffer = NettyByteBufferFactory.DEFAULT.copiedBuffer("late".getBytes(StandardCharsets.UTF_8));
        ByteBuf wrapped = buffer.asNativeBuffer();
        support.discard(HttpResponse.ok(buffer));
        assertEquals(0, wrapped.refCnt());

        // a body that holds nothing is left alone
        support.discard(HttpResponse.ok("text"));
    }

    @Test
    void aResponseWithBytesIsPreparedOnce() {
        byte[] bytes = "prepared".getBytes(StandardCharsets.UTF_8);
        PreparedDirectResponse prepared = (PreparedDirectResponse) support.prepare(HttpResponse.ok(bytes)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .header("X-Version", "1"));
        assertNotNull(prepared);
        assertEquals(200, prepared.code());
        assertEquals("1", prepared.getHeaders().get("X-Version"));
        assertEquals(MediaType.TEXT_PLAIN, prepared.getHeaders().get(HttpHeaders.CONTENT_TYPE));

        // each request writes a duplicate of the shared buffer, and releases it
        ByteBuf first = prepared.content();
        assertEquals("prepared", first.toString(StandardCharsets.UTF_8));
        first.release();
        ByteBuf second = prepared.content();
        assertEquals("prepared", second.toString(StandardCharsets.UTF_8));
        second.release();

        // each request gets its own headers
        io.netty.handler.codec.http.HttpResponse head = prepared.head();
        head.headers().set("X-Request", "1");
        assertNull(prepared.head().headers().get("X-Request"));

        // a response without a body is prepared too
        assertNotNull(support.prepare(HttpResponse.noContent()));
        // a body a message body writer writes is written for each request
        assertNull(support.prepare(HttpResponse.ok(new Object())));
        support.close();
    }
}
