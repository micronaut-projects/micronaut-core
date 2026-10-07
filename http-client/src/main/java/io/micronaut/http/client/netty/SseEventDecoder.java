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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.sse.Event;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Assembles the lines of an event stream, as split by {@link SseSplitter}, into events, following the
 * <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation">event stream
 * interpretation</a>: a leading byte order mark is skipped, the {@code data} lines of an event are joined with a line
 * feed, comments are ignored, an event is dispatched at a blank line, and an event carries the last event id seen. An
 * event not terminated by a blank line at the end of the stream is discarded. An {@code id} containing a null character
 * and a {@code retry} that is not a number of milliseconds are ignored.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
final class SseEventDecoder {
    private static final byte[] BOM = "\uFEFF".getBytes(StandardCharsets.UTF_8);

    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private final long maxDataSize;
    private boolean firstLine = true;
    private boolean hasData;
    @Nullable
    private String id;
    @Nullable
    private String name;
    @Nullable
    private Duration retry;

    /**
     * @param maxDataSize The maximum size of the data of one event
     */
    SseEventDecoder(long maxDataSize) {
        this.maxDataSize = maxDataSize;
    }

    /**
     * Decode one line. The line is not released.
     *
     * @param line The line, without its line terminator
     * @return The event the line completes, if any
     */
    List<Event<byte[]>> line(ByteBuf line) {
        int start = line.readerIndex();
        int length = line.readableBytes();
        if (firstLine) {
            firstLine = false;
            if (length >= BOM.length && ByteBufUtil.equals(line, start, Unpooled.wrappedBuffer(BOM), 0, BOM.length)) {
                start += BOM.length;
                length -= BOM.length;
            }
        }
        if (length == 0) {
            return dispatch();
        }
        int colon = line.indexOf(start, start + length, (byte) ':');
        if (colon == start) {
            // comment
            return List.of();
        }
        String field;
        int valueStart;
        if (colon < 0) {
            field = line.toString(start, length, StandardCharsets.UTF_8);
            valueStart = start + length;
        } else {
            field = line.toString(start, colon - start, StandardCharsets.UTF_8);
            valueStart = colon + 1;
            if (valueStart < start + length && line.getByte(valueStart) == ' ') {
                valueStart++;
            }
        }
        int valueLength = start + length - valueStart;
        switch (field) {
            case "data" -> {
                long size = (long) data.size() + valueLength + 1;
                if (size > maxDataSize) {
                    throw new ContentLengthExceededException(maxDataSize, size);
                }
                if (hasData) {
                    data.write('\n');
                }
                hasData = true;
                byte[] value = new byte[valueLength];
                line.getBytes(valueStart, value);
                data.writeBytes(value);
            }
            case "event" -> name = line.toString(valueStart, valueLength, StandardCharsets.UTF_8);
            case "id" -> {
                String value = line.toString(valueStart, valueLength, StandardCharsets.UTF_8);
                if (value.indexOf('\0') < 0) {
                    id = value;
                }
            }
            case "retry" -> {
                String value = line.toString(valueStart, valueLength, StandardCharsets.UTF_8);
                if (!value.isEmpty() && value.chars().allMatch(c -> c >= '0' && c <= '9')) {
                    try {
                        retry = Duration.ofMillis(Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        // too large, ignored
                    }
                }
            }
            default -> {
                // ignore unknown fields
            }
        }
        return List.of();
    }

    private List<Event<byte[]>> dispatch() {
        if (!hasData) {
            // an event without data is not dispatched
            name = null;
            retry = null;
            return List.of();
        }
        Event<byte[]> event = Event.of(data.toByteArray())
            .name(name)
            .id(id)
            .retry(retry);
        data.reset();
        hasData = false;
        name = null;
        retry = null;
        return List.of(event);
    }
}
