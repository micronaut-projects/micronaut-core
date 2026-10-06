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
package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.sse.EventStreamDecoder;
import io.micronaut.http.sse.Event;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The event stream reader of the Netty buffers of the client, on heap and direct buffers: the
 * same events as the decoder of byte arrays, whatever the pieces.
 */
class NettyEventStreamReaderTest {

    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lineTerminators(boolean direct) throws IOException {
        assertEquals(List.of("a", "b", "c", "d"), data(read(direct, "data: a\n\ndata: b\r\n\r\ndata: c\r\rdata: d\n\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminatorsSplitBetweenPieces(boolean direct) throws IOException {
        assertEquals(List.of("a", "b"), data(read(direct, "data: a\r", "\n\r", "\n", "data: b\r", "\n", "\r\n")));
        assertEquals(List.of("a", "b"), data(read(direct, "data: a\r", "\rdata: b\n\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void linesSplitBetweenPieces(boolean direct) throws IOException {
        assertEquals(List.of("hello\nworld"), data(read(direct, "da", "ta: hel", "lo\ndata: wo", "rld\n", "\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theSameEventsAsTheDecoderOfArraysByteByByte(boolean direct) throws IOException {
        String stream = "﻿id: 1\r\nevent: greeting\r\ndata: hello\r\ndata: world\r\n\r\n: comment\ndata: {\"a\":1}\nretry: 1000\n\ndata: last\r\r";
        byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
        List<String> pieces = new ArrayList<>();
        for (byte b : bytes) {
            pieces.add(new String(new byte[] {b}, StandardCharsets.ISO_8859_1));
        }
        List<Event<String>> byteByByte = read(direct, StandardCharsets.ISO_8859_1, pieces.toArray(new String[0]));
        List<Event<byte[]>> expected = new EventStreamDecoder(1024).decode(bytes);

        assertEquals(describe(expected), describeStrings(byteByByte));
        assertEquals(List.of("hello\nworld", "{\"a\":1}", "last"), data(byteByByte));
        assertEquals("1", byteByByte.get(2).getId());
        assertEquals(Duration.ofMillis(1000), byteByByte.get(1).getRetry());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aLongLineFails(boolean direct) {
        assertThrows(ContentLengthExceededException.class, () -> read(direct, 8, "data: 0123456789\n\n"));
        assertThrows(ContentLengthExceededException.class, () -> read(direct, 8, "data: 0", "123456789"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void thePiecesAreReleased(boolean direct) throws IOException {
        List<ByteBuf> buffers = new ArrayList<>();
        NettyEventStreamReader<String> reader = new NettyEventStreamReader<>(new EventStreamDecoder(1024), bytes -> new String(bytes, StandardCharsets.UTF_8));
        for (String piece : List.of("data: a\n", "\ndata: b", "\n\n")) {
            ByteBuf buf = buffer(direct, piece.getBytes(StandardCharsets.UTF_8));
            buffers.add(buf);
            reader.read(READ_BUFFERS.adapt(buf));
        }
        reader.close();
        for (ByteBuf buf : buffers) {
            assertEquals(0, buf.refCnt());
        }
    }

    private static List<Event<String>> read(boolean direct, String... pieces) throws IOException {
        return read(direct, 1024, pieces);
    }

    private static List<Event<String>> read(boolean direct, long limit, String... pieces) throws IOException {
        NettyEventStreamReader<String> reader = new NettyEventStreamReader<>(new EventStreamDecoder(limit), bytes -> new String(bytes, StandardCharsets.UTF_8));
        List<Event<String>> events = new ArrayList<>();
        for (String piece : pieces) {
            reader.read(READ_BUFFERS.adapt(buffer(direct, piece.getBytes(StandardCharsets.UTF_8))));
            drain(reader, events);
        }
        reader.complete();
        drain(reader, events);
        return events;
    }

    private static List<Event<String>> read(boolean direct, Charset charset, String... pieces) throws IOException {
        NettyEventStreamReader<String> reader = new NettyEventStreamReader<>(new EventStreamDecoder(1024), bytes -> new String(bytes, StandardCharsets.UTF_8));
        List<Event<String>> events = new ArrayList<>();
        for (String piece : pieces) {
            reader.read(READ_BUFFERS.adapt(buffer(direct, piece.getBytes(charset))));
            drain(reader, events);
        }
        reader.complete();
        drain(reader, events);
        return events;
    }

    private static void drain(NettyEventStreamReader<String> reader, List<Event<String>> events) {
        Event<String> event;
        while ((event = reader.poll()) != null) {
            events.add(event);
        }
    }

    private static ByteBuf buffer(boolean direct, byte[] bytes) {
        if (!direct) {
            return Unpooled.wrappedBuffer(bytes);
        }
        ByteBuf buf = Unpooled.directBuffer(bytes.length);
        buf.writeBytes(bytes);
        return buf;
    }

    private static List<String> data(List<Event<String>> events) {
        return events.stream().map(Event::getData).toList();
    }

    private static List<String> describe(List<Event<byte[]>> events) {
        return events.stream().map(e -> new String(e.getData(), StandardCharsets.UTF_8) + "|" + e.getId() + "|" + e.getName() + "|" + e.getRetry()).toList();
    }

    private static List<String> describeStrings(List<Event<String>> events) {
        return events.stream().map(e -> e.getData() + "|" + e.getId() + "|" + e.getName() + "|" + e.getRetry()).toList();
    }
}
