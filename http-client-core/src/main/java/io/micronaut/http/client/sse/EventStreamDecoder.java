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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.sse.Event;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the bytes of an event stream into events, as they arrive in pieces of any size,
 * following the
 * <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation">event
 * stream interpretation</a>: lines end with a carriage return, a line feed, or both, a leading
 * byte order mark is skipped, the {@code data} lines of an event are joined with a line feed,
 * comments are ignored, an event is dispatched at a blank line, and an event carries the last
 * event id seen. An event not terminated by a blank line at the end of the stream is discarded.
 * An {@code id} containing a null character and a {@code retry} that is not a number of
 * milliseconds are ignored.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class EventStreamDecoder {
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final long maxBufferSize;
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private boolean firstLine = true;
    /**
     * The previous piece ended with a carriage return: a line feed that starts this one ends no
     * other line.
     */
    private boolean skipLineFeed;
    private boolean hasData;
    private @Nullable String id;
    private @Nullable String name;
    private @Nullable Duration retry;

    /**
     * @param maxBufferSize The maximum size of a line, and of the data of one event
     */
    EventStreamDecoder(long maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * Decode the next piece of the stream.
     *
     * @param bytes The bytes of the piece
     * @return The events the piece completes
     * @throws ContentLengthExceededException if a line or the data of an event exceeds the limit
     */
    List<Event<byte[]>> decode(byte[] bytes) {
        List<Event<byte[]>> events = null;
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b != CR && b != LF) {
                continue;
            }
            if (b == LF && skipLineFeed && i == start && line.size() == 0) {
                // the second half of a CRLF split between two pieces
                skipLineFeed = false;
                start = i + 1;
                continue;
            }
            append(bytes, start, i - start);
            Event<byte[]> event = endLine();
            if (event != null) {
                if (events == null) {
                    events = new ArrayList<>(2);
                }
                events.add(event);
            }
            if (b == CR) {
                if (i + 1 < bytes.length) {
                    if (bytes[i + 1] == LF) {
                        i++;
                    }
                } else {
                    skipLineFeed = true;
                }
            }
            start = i + 1;
        }
        if (start < bytes.length) {
            skipLineFeed = false;
            append(bytes, start, bytes.length - start);
        }
        return events == null ? List.of() : events;
    }

    private void append(byte[] bytes, int offset, int length) {
        if (length == 0) {
            return;
        }
        long size = (long) line.size() + length;
        if (size > maxBufferSize) {
            throw new ContentLengthExceededException(maxBufferSize, size);
        }
        line.write(bytes, offset, length);
    }

    private @Nullable Event<byte[]> endLine() {
        skipLineFeed = false;
        byte[] bytes = line.toByteArray();
        line.reset();
        int start = 0;
        if (firstLine) {
            firstLine = false;
            if (bytes.length >= BOM.length && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
                start = BOM.length;
            }
        }
        int length = bytes.length - start;
        if (length == 0) {
            return dispatch();
        }
        if (bytes[start] == ':') {
            // comment
            return null;
        }
        int colon = indexOf(bytes, start, (byte) ':');
        String field;
        int valueStart;
        if (colon < 0) {
            field = new String(bytes, start, length, StandardCharsets.UTF_8);
            valueStart = bytes.length;
        } else {
            field = new String(bytes, start, colon - start, StandardCharsets.UTF_8);
            valueStart = colon + 1;
            if (valueStart < bytes.length && bytes[valueStart] == ' ') {
                valueStart++;
            }
        }
        int valueLength = bytes.length - valueStart;
        switch (field) {
            case "data" -> {
                long size = (long) data.size() + valueLength + 1;
                if (size > maxBufferSize) {
                    throw new ContentLengthExceededException(maxBufferSize, size);
                }
                if (hasData) {
                    data.write(LF);
                }
                hasData = true;
                data.write(bytes, valueStart, valueLength);
            }
            case "event" -> name = new String(bytes, valueStart, valueLength, StandardCharsets.UTF_8);
            case "id" -> {
                String value = new String(bytes, valueStart, valueLength, StandardCharsets.UTF_8);
                if (value.indexOf('\0') < 0) {
                    id = value;
                }
            }
            case "retry" -> {
                String value = new String(bytes, valueStart, valueLength, StandardCharsets.UTF_8);
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
        return null;
    }

    private @Nullable Event<byte[]> dispatch() {
        if (!hasData) {
            // an event without data is not dispatched
            name = null;
            retry = null;
            return null;
        }
        Event<byte[]> event = Event.of(data.toByteArray())
            .name(name)
            .id(id)
            .retry(retry);
        data.reset();
        hasData = false;
        name = null;
        retry = null;
        return event;
    }

    private static int indexOf(byte[] bytes, int from, byte b) {
        for (int i = from; i < bytes.length; i++) {
            if (bytes[i] == b) {
                return i;
            }
        }
        return -1;
    }
}
