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

import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.sse.Event;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseEventDecoderTest {

    private static List<Event<byte[]>> decode(String stream) {
        return decode(stream, Long.MAX_VALUE);
    }

    private static List<Event<byte[]>> decode(String stream, long maxDataSize) {
        ByteBuf buf = Unpooled.copiedBuffer(stream, StandardCharsets.UTF_8);
        List<ByteBuf> lines = SseSplitter.split(buf);
        SseEventDecoder decoder = new SseEventDecoder(maxDataSize);
        List<Event<byte[]>> events = new ArrayList<>();
        try {
            for (ByteBuf line : lines) {
                if (line != lines.get(lines.size() - 1)) {
                    events.addAll(decoder.line(line));
                }
            }
        } finally {
            lines.forEach(ByteBuf::release);
        }
        return events;
    }

    private static String data(Event<byte[]> event) {
        return new String(event.getData(), StandardCharsets.UTF_8);
    }

    @Test
    void fields() {
        List<Event<byte[]>> events = decode("﻿data: one\r\n: a comment\nevent: greeting\nid: 7\nretry: 1000\n\ndata:two\ndata: lines\n\n");

        assertEquals(2, events.size());
        assertEquals("one", data(events.get(0)));
        assertEquals("greeting", events.get(0).getName());
        assertEquals("7", events.get(0).getId());
        assertEquals(Duration.ofSeconds(1), events.get(0).getRetry());
        assertEquals("two\nlines", data(events.get(1)));
        assertNull(events.get(1).getName());
        // the last event id carries over
        assertEquals("7", events.get(1).getId());
        assertNull(events.get(1).getRetry());
    }

    @Test
    void eventWithoutDataIsNotDispatched() {
        List<Event<byte[]>> events = decode("event: empty\n\ndata\n\n");

        assertEquals(1, events.size());
        assertEquals("", data(events.get(0)));
        assertNull(events.get(0).getName());
    }

    @Test
    void unterminatedEventIsDiscarded() {
        List<Event<byte[]>> events = decode("data: one\n\ndata: two\n");

        assertEquals(1, events.size());
        assertEquals("one", data(events.get(0)));
    }

    @Test
    void invalidIdAndRetryAreIgnored() {
        List<Event<byte[]>> events = decode("id: 1\n\ndata: a\n\nid: x\0y\nretry: 99999999999999999999\ndata: b\n\n");

        assertEquals(2, events.size());
        assertEquals("1", events.get(1).getId());
        assertNull(events.get(1).getRetry());
    }

    @Test
    void dataIsBounded() {
        assertThrows(ContentLengthExceededException.class, () -> decode("data: 12345\ndata: 67890\n\n", 8));
    }
}
