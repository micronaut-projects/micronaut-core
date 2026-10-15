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
package io.micronaut.http.client.sse;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reader of the events of an event stream on the Netty buffers of the client: a heap buffer
 * is decoded in its array, a direct one through an array the reader reuses, with the same events
 * as the decoder of byte arrays.
 */
class EventStreamReaderTest {

    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);
    private static final MessageBodyHandlerRegistry REGISTRY = new ContextlessMessageBodyHandlerRegistry(new ApplicationConfiguration(), NettyByteBufferFactory.DEFAULT);
    private static final Argument<byte[]> BYTES = Argument.of(byte[].class);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lineTerminators(boolean direct) throws IOException {
        assertEquals(List.of("a", "b", "c", "d"), data(read(direct, 1024, "data: a\n\ndata: b\r\n\r\ndata: c\r\rdata: d\n\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void terminatorsSplitBetweenPieces(boolean direct) throws IOException {
        assertEquals(List.of("a", "b"), data(read(direct, 1024, "data: a\r", "\n\r", "\n", "data: b\r", "\n", "\r\n")));
        assertEquals(List.of("a", "b"), data(read(direct, 1024, "data: a\r", "\rdata: b\n\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void linesSplitBetweenPieces(boolean direct) throws IOException {
        assertEquals(List.of("hello\nworld"), data(read(direct, 1024, "da", "ta: hel", "lo\ndata: wo", "rld\n", "\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aShorterPieceAfterALongerOne(boolean direct) throws IOException {
        String longer = "data: " + "x".repeat(100) + "\n\n";
        assertEquals(List.of("x".repeat(100), "a", "y".repeat(50)), data(read(direct, 1024, longer, "data: a\n\n", "data: " + "y".repeat(50) + "\n\n")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theSameEventsAsTheDecoderOfArraysByteByByte(boolean direct) throws IOException {
        String stream = "﻿id: 1\r\nevent: greeting\r\ndata: hello\r\ndata: world\r\n\r\n: comment\ndata: {\"a\":1}\nretry: 1000\n\ndata: last\r\r";
        byte[] bytes = stream.getBytes(StandardCharsets.UTF_8);
        List<byte[]> pieces = new ArrayList<>();
        for (byte b : bytes) {
            pieces.add(new byte[] {b});
        }

        List<Event<byte[]>> byteByByte = read(direct, 1024, pieces);

        assertEquals(describe(new EventStreamDecoder(1024).decode(bytes)), describe(byteByByte));
        assertEquals(List.of("hello\nworld", "{\"a\":1}", "last"), data(byteByByte));
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
        try (PieceReader<Event<byte[]>> reader = EventStreams.reader(REGISTRY, BYTES, new SimpleHttpHeaders(), 1024)) {
            for (String piece : List.of("data: a\n", "\ndata: b", "\n\n")) {
                ByteBuf buf = buffer(direct, piece.getBytes(StandardCharsets.UTF_8));
                buffers.add(buf);
                reader.read(READ_BUFFERS.adapt(buf));
            }
        }
        for (ByteBuf buf : buffers) {
            assertEquals(0, buf.refCnt());
        }
    }

    private static List<Event<byte[]>> read(boolean direct, long limit, String... pieces) throws IOException {
        List<byte[]> arrays = new ArrayList<>();
        for (String piece : pieces) {
            arrays.add(piece.getBytes(StandardCharsets.UTF_8));
        }
        return read(direct, limit, arrays);
    }

    private static List<Event<byte[]>> read(boolean direct, long limit, List<byte[]> pieces) throws IOException {
        List<Event<byte[]>> events = new ArrayList<>();
        try (PieceReader<Event<byte[]>> reader = EventStreams.reader(REGISTRY, BYTES, new SimpleHttpHeaders(), limit)) {
            for (byte[] piece : pieces) {
                reader.read(READ_BUFFERS.adapt(buffer(direct, piece)));
                Event<byte[]> event;
                while ((event = reader.poll()) != null) {
                    events.add(event);
                }
            }
            reader.complete();
            Event<byte[]> event;
            while ((event = reader.poll()) != null) {
                events.add(event);
            }
        }
        return events;
    }

    private static ByteBuf buffer(boolean direct, byte[] bytes) {
        if (!direct) {
            return Unpooled.wrappedBuffer(bytes);
        }
        ByteBuf buf = Unpooled.directBuffer(bytes.length);
        buf.writeBytes(bytes);
        return buf;
    }

    private static List<String> data(List<Event<byte[]>> events) {
        return events.stream().map(e -> new String(e.getData(), StandardCharsets.UTF_8)).toList();
    }

    private static List<String> describe(List<Event<byte[]>> events) {
        return events.stream().map(e -> new String(e.getData(), StandardCharsets.UTF_8) + "|" + e.getId() + "|" + e.getName() + "|" + e.getRetry()).toList();
    }
}
