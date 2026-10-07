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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

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
public final class EventStreamDecoder {
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final long CR_PATTERN = 0x0D0D0D0D0D0D0D0DL;
    private static final long LF_PATTERN = 0x0A0A0A0A0A0A0A0AL;
    private static final long LOW_BITS = 0x0101010101010101L;
    private static final long HIGH_BITS = 0x8080808080808080L;

    private final long maxBufferSize;
    /**
     * The start of a line that continues in the next piece.
     */
    private final LineBuffer line = new LineBuffer();
    /**
     * The data of an event with more than one {@code data} line.
     */
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    /**
     * The data of an event with one {@code data} line so far, copied once.
     */
    private byte @Nullable [] firstData;
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
    public EventStreamDecoder(long maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    /**
     * Decode the next piece of the stream.
     *
     * @param bytes The bytes of the piece
     * @return The events the piece completes
     * @throws ContentLengthExceededException if a line or the data of an event exceeds the limit
     */
    public List<Event<byte[]>> decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    /**
     * Decode the next piece of the stream. A line that ends in the piece is read in place; only
     * the start of a line that continues in the next piece is copied.
     *
     * @param bytes  The array of the piece
     * @param offset The offset of the piece in the array
     * @param length The length of the piece
     * @return The events the piece completes
     * @throws ContentLengthExceededException if a line or the data of an event exceeds the limit
     */
    public List<Event<byte[]>> decode(byte[] bytes, int offset, int length) {
        List<Event<byte[]>> events = new ArrayList<>(2);
        decode(bytes, offset, length, events::add);
        return events;
    }

    /**
     * Decode the next piece of the stream, like {@link #decode(byte[], int, int)}. Each event is
     * handed to the consumer as soon as its blank line is read, so the events before a line that
     * exceeds the limit are handed out before the failure.
     *
     * @param bytes  The array of the piece
     * @param offset The offset of the piece in the array
     * @param length The length of the piece
     * @param out    Takes the events the piece completes
     * @throws ContentLengthExceededException if a line or the data of an event exceeds the limit
     */
    public void decode(byte[] bytes, int offset, int length, Consumer<? super Event<byte[]>> out) {
        int end = offset + length;
        // eight bytes at a time, without reflection
        ByteBuffer words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int start = offset;
        while (true) {
            int i = lineEnd(bytes, words, start, end);
            if (i < 0) {
                break;
            }
            byte b = bytes[i];
            if (b == LF && skipLineFeed && i == start && line.size() == 0) {
                // the second half of a CRLF split between two pieces
                skipLineFeed = false;
                start = i + 1;
                continue;
            }
            Event<byte[]> event;
            if (line.size() == 0) {
                checkLine(i - start);
                event = endLine(bytes, start, i - start);
            } else {
                append(bytes, start, i - start);
                event = endLine(line.buffer(), 0, line.size());
                line.reset();
            }
            if (event != null) {
                out.accept(event);
            }
            if (b == CR) {
                if (i + 1 < end) {
                    if (bytes[i + 1] == LF) {
                        i++;
                    }
                } else {
                    skipLineFeed = true;
                }
            }
            start = i + 1;
        }
        if (start < end) {
            skipLineFeed = false;
            append(bytes, start, end - start);
        }
    }

    /**
     * The index of the next carriage return or line feed, eight bytes at a time.
     */
    private static int lineEnd(byte[] bytes, ByteBuffer words, int from, int end) {
        int i = from;
        for (; i + Long.BYTES <= end; i += Long.BYTES) {
            long word = words.getLong(i);
            long found = zeroBytes(word ^ LF_PATTERN) | zeroBytes(word ^ CR_PATTERN);
            if (found != 0) {
                // the lowest flagged byte is always a match
                return i + (Long.numberOfTrailingZeros(found) >>> 3);
            }
        }
        for (; i < end; i++) {
            byte b = bytes[i];
            if (b == CR || b == LF) {
                return i;
            }
        }
        return -1;
    }

    private static long zeroBytes(long word) {
        return (word - LOW_BITS) & ~word & HIGH_BITS;
    }

    private void checkLine(int length) {
        if (length > maxBufferSize) {
            throw new ContentLengthExceededException(maxBufferSize, length);
        }
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

    private @Nullable Event<byte[]> endLine(byte[] bytes, int offset, int length) {
        skipLineFeed = false;
        int start = offset;
        int end = offset + length;
        if (firstLine) {
            firstLine = false;
            if (length >= BOM.length && bytes[start] == BOM[0] && bytes[start + 1] == BOM[1] && bytes[start + 2] == BOM[2]) {
                start += BOM.length;
            }
        }
        if (start == end) {
            return dispatch();
        }
        if (bytes[start] == ':') {
            // comment
            return null;
        }
        int colon = indexOf(bytes, start, end, (byte) ':');
        String field;
        int valueStart;
        if (colon < 0) {
            field = new String(bytes, start, end - start, StandardCharsets.UTF_8);
            valueStart = end;
        } else {
            field = new String(bytes, start, colon - start, StandardCharsets.UTF_8);
            valueStart = colon + 1;
            if (valueStart < end && bytes[valueStart] == ' ') {
                valueStart++;
            }
        }
        int valueLength = end - valueStart;
        switch (field) {
            case "data" -> {
                long size = (long) (firstData == null ? data.size() : firstData.length) + valueLength + 1;
                if (size > maxBufferSize) {
                    throw new ContentLengthExceededException(maxBufferSize, size);
                }
                if (!hasData) {
                    hasData = true;
                    firstData = Arrays.copyOfRange(bytes, valueStart, end);
                } else {
                    if (firstData != null) {
                        data.write(firstData, 0, firstData.length);
                        firstData = null;
                    }
                    data.write(LF);
                    data.write(bytes, valueStart, valueLength);
                }
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
        byte[] bytes = firstData == null ? data.toByteArray() : firstData;
        Event<byte[]> event = Event.of(bytes)
            .name(name)
            .id(id)
            .retry(retry);
        data.reset();
        firstData = null;
        hasData = false;
        name = null;
        retry = null;
        return event;
    }

    private static int indexOf(byte[] bytes, int from, int end, byte b) {
        for (int i = from; i < end; i++) {
            if (bytes[i] == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A buffer of a line that exposes its array, so that the line is read in place.
     */
    private static final class LineBuffer extends ByteArrayOutputStream {
        byte[] buffer() {
            return buf;
        }
    }
}
