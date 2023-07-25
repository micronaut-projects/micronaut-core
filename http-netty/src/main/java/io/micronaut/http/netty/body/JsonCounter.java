/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.JsonSyntaxException;
import io.netty.buffer.ByteBuf;

/**
 * This class takes in JSON data and does simple parsing to detect boundaries between json nodes.
 * For example, this class can recognize the separation between the two JSON objects in
 * {@code {"foo":"bar"} {"bar":"baz"}}.<br>
 * Public for fuzzing.
 */
@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "InnerAssignment"})
@Internal
public final class JsonCounter {
    /**
     * Total number of bytes consumed.
     */
    private long position;
    /**
     * Depth of nested structures.
     */
    private int depth;
    /**
     * Current state of the parser.
     */
    private State state = State.BASE;

    /**
     * {@link #position} of the first byte of the current top-level JSON node.
     */
    private long bufferStart = -1;

    /**
     * Whether we are currently unwrapping a top-level array.
     *
     * @see #unwrapTopLevelArray()
     */
    private boolean unwrappingArray;
    /**
     * Whether we are currently unwrapping a top-level array, and expect a comma next (or end of
     * array).
     *
     * @see #unwrapTopLevelArray()
     */
    private boolean allowUnwrappingArrayComma;

    /**
     * The region of the last complete top-level JSON node we have visited. Polled by the user.
     */
    @Nullable
    private BufferRegion lastFlushedRegion;

    /**
     * Parse some input data. If {@code buf} is readable, this method always advances (always
     * consumes at least one byte).
     *
     * @param buf The input buffer
     * @throws JsonSyntaxException If there is a syntax error in the JSON. Note that not all syntax
     *                             errors are detected by this class.
     */
    public void feed(ByteBuf buf) throws JsonSyntaxException {
        if (position < 4) {
            // RFC 4627 allows JSON to be encoded as UTF-8, UTF-16 or UTF-32. It also specifies a
            // charset detection algorithm using 0x00 bytes.
            // Later standards (RFC 8259) only permit UTF-8, but Jackson still allows other
            // charsets. To avoid potential parser differential vulnerabilities, we forbid any 0x00
            // bytes in the input. They never appear in valid UTF-8 JSON.

            // If the input is utf-16 or utf-32, one of the first four bytes will be 0. Checking
            // this separately and only for four bytes allows us to avoid the work in the hot loops
            // below.
            int r = buf.readableBytes();
            if ((r >= 1 && buf.getByte(0) == 0)
                || (r >= 2 && buf.getByte(1) == 0)
                || (r >= 3 && buf.getByte(2) == 0)
                || (r >= 4 && buf.getByte(3) == 0)) {

                throw new JsonSyntaxException("Input must be legal UTF-8 JSON");
            }
        }
        if (!isBuffering()) {
            proceedUntilBuffering(buf);
        }
        if (isBuffering()) {
            proceedUntilNonBuffering(buf);
        }
    }

    /**
     * Enable top-level array unwrapping: If the input starts with an array, that array's elements
     * are returned as individual JSON nodes, not the array all at once. <br>
     * Must be called before any data is processed, but can be called after
     * {@link #noTokenization()}.
     */
    public void unwrapTopLevelArray() {
        if (position != 0) {
            throw new IllegalStateException("Already consumed input");
        }
        state = State.BEFORE_UNWRAP_ARRAY;
        bufferStart = -1;
    }

    /**
     * Do not perform any tokenization, assume that there is only one root-level value. There is
     * still some basic validation (ensuring the input isn't utf-16 or utf-32).
     */
    public void noTokenization() {
        if (position != 0) {
            throw new IllegalStateException("Already consumed input");
        }
        state = State.BUFFER_ALL;
        bufferStart = 0;
    }

    /**
     * Proceed until {@link #isBuffering()} becomes false.
     */
    @SuppressWarnings("java:S3776")
    private void proceedUntilNonBuffering(ByteBuf buf) throws JsonSyntaxException {
        assert isBuffering();
        int end = buf.writerIndex();

        int i = buf.readerIndex();
        while (i < end && bufferStart != -1) {
            int start = i;
            if (state == State.BASE) {
                assert depth > 0 : depth;
                for (; i < end; i++) {
                    if (!skipBufferingBase(buf.getByte(i))) {
                        break;
                    }
                }
                this.position += i - start;
                if (i < end) {
                    handleBufferingBaseSpecial(buf.getByte(i));
                    i++;
                    position++;
                }
            } else if (state == State.STRING) {
                for (; i < end; i++) {
                    if (!skipString(buf.getByte(i))) {
                        break;
                    }
                }
                this.position += i - start;
                if (i < end) {
                    handleStringSpecial(buf.getByte(i));
                    i++;
                    position++;
                }
            } else if (state == State.ESCAPE) {
                handleEscape(buf.getByte(i));
                i++;
                position++;
            } else if (state == State.TOP_LEVEL_SCALAR) {
                assert depth == 0 : depth;
                for (; i < end; i++) {
                    if (!skipTopLevelScalar(buf.getByte(i))) {
                        break;
                    }
                }
                this.position += i - start;
                if (i < end) {
                    handleTopLevelScalarSpecial(buf.getByte(i));
                    i++;
                    position++;
                }
            } else if (state == State.BUFFER_ALL) {
                i = end;
                position += i - start;
            } else {
                throw new AssertionError(state);
            }
        }
        buf.readerIndex(i);
    }

    /**
     * Consume some input until {@link #isBuffering()}. Sometimes this method returns before that
     * is the case, to make the implementation simpler.
     */
    @SuppressWarnings("java:S3776")
    private void proceedUntilBuffering(ByteBuf buf) throws JsonSyntaxException {
        assert !isBuffering();
        assert depth == 0 : depth;

        int start = buf.readerIndex();
        int end = buf.writerIndex();
        int i = start;

        if (state == State.AFTER_UNWRAP_ARRAY) {
            // top-level array consumed. reject further data
            skipWs(buf, i, end);
            if (i < end) {
                throw new JsonSyntaxException("Superfluous data after top-level array in streaming mode");
            }
        } else {
            // normal path
            assert state == State.BASE || state == State.BEFORE_UNWRAP_ARRAY : state;

            if (position == 0 && i < end && buf.getByte(i) == (byte) 0xef) {
                throw new JsonSyntaxException("UTF-8 BOM not allowed");
            }

            // if we are unwrapping a top-level array, search for a comma
            if (allowUnwrappingArrayComma) {
                i = skipWs(buf, i, end);
                if (i < end && buf.getByte(i) == ',') {
                    allowUnwrappingArrayComma = false;
                    i++;
                }
            }
            i = skipWs(buf, i, end);
            this.position += i - start;

            if (i < end) {
                byte b = buf.getByte(i);
                handleNonBufferingBase(b);
                i++;
                position++;
            }
        }

        buf.readerIndex(i);
    }

    /**
     * Skip any whitespace characters.
     *
     * @param i   The start index
     * @param end The maximum index
     * @return The first non-whitespace character index, or {@code end}
     */
    private static int skipWs(ByteBuf buf, int i, int end) {
        for (; i < end; i++) {
            if (!ws(buf.getByte(i))) {
                break;
            }
        }
        return i;
    }

    /**
     * Handle a special byte (anything but whitespace) in the base state, while not buffering.
     */
    private void handleNonBufferingBase(byte b) throws JsonSyntaxException {
        switch (b) {
            case '}' -> failMismatchedBrackets();
            case ']' -> {
                if (unwrappingArray) {
                    state = State.AFTER_UNWRAP_ARRAY;
                } else {
                    failMismatchedBrackets();
                }
            }
            case '{' -> {
                depth = 1;
                bufferStart = position;
                state = State.BASE; // we might be in BEFORE_UNWRAP_ARRAY
            }
            case '[' -> {
                if (state == State.BEFORE_UNWRAP_ARRAY) {
                    state = State.BASE;
                    unwrappingArray = true;
                } else {
                    depth = 1;
                    bufferStart = position;
                }
            }
            case '"' -> {
                state = State.STRING;
                bufferStart = position;
            }
            default -> {
                state = State.TOP_LEVEL_SCALAR;
                bufferStart = position;
            }
        }
    }

    /**
     * @return {@code true} if this character does not end the top-level scalar
     */
    private static boolean skipTopLevelScalar(byte b) {
        return !ws(b) && b != '"' && b != '{' && b != '[' && b != ']' && b != '}' && b != ',';
    }

    /**
     * Handle a special byte (anything but {@link #skipTopLevelScalar}) in the
     * {@link State#TOP_LEVEL_SCALAR} state.
     */
    private void handleTopLevelScalarSpecial(byte b) throws JsonSyntaxException {
        if (ws(b)) {
            position--;
            flushAfter();
            position++;
            allowUnwrappingArrayComma = unwrappingArray;
            state = State.BASE;
        } else if (unwrappingArray && (b == ',' || b == ']')) {
            position--;
            flushAfter();
            position++;
            if (b == ',') {
                state = State.BASE;
            } else {
                state = State.AFTER_UNWRAP_ARRAY;
            }
            allowUnwrappingArrayComma = false;
        } else {
            failMissingWs();
        }
    }

    /**
     * Handle a byte in the {@link State#ESCAPE} state.
     */
    private void handleEscape(byte b) {
        state = State.STRING;
    }

    /**
     * @return {@code true} if this character does not end the string
     */
    private static boolean skipString(byte b) {
        return b != '"' && b != '\\';
    }

    /**
     * Handle a special byte (anything but {@link #skipString}) in the {@link State#STRING} state.
     */
    private void handleStringSpecial(byte b) throws JsonSyntaxException {
        switch (b) {
            case '"' -> {
                state = State.BASE;
                if (depth == 0) {
                    flushAfter();
                }
            }
            case '\\' -> state = State.ESCAPE;
            default -> throw new AssertionError();
        }
    }

    /**
     * @return {@code true} if this character does not change the state while in {@link State#BASE}
     * and while not buffering
     */
    @SuppressWarnings("java:S2178") // performance
    private static boolean skipBufferingBase(byte b) {
        return (b != '"') & (b != '{') & (b != '[') & (b != ']') & (b != '}');
    }

    /**
     * Handle a special byte (anything but {@link #skipBufferingBase(byte)}) in the base state,
     * while buffering.
     */
    private void handleBufferingBaseSpecial(byte b) throws JsonSyntaxException {
        switch (b) {
            case '}', ']' -> {
                depth--;
                if (depth == 0) {
                    flushAfter();
                }
            }
            case '{', '[' -> depth = Math.incrementExact(depth);
            case '"' -> state = State.STRING;
            default -> throw new AssertionError(b);
        }
    }

    /**
     * Flush the current JSON node, starting at {@link #bufferStart}, and ending after
     * {@link #position}. Disables buffering.
     */
    private void flushAfter() {
        if (lastFlushedRegion != null) {
            throw new IllegalStateException("Should have cleared last buffer region");
        }
        assert bufferStart != -1;
        assert position >= bufferStart;
        lastFlushedRegion = new BufferRegion(bufferStart, position + 1);
        bufferStart = -1;
        allowUnwrappingArrayComma = unwrappingArray;
    }

    /**
     * Check for any new flushed data from the last {@link #feed(ByteBuf)} operation.
     *
     * @return The region that contains a JSON node, relative to {@link #position()}, or
     * {@code null} if the JSON node has not completed yet.
     */
    @Nullable
    public BufferRegion pollFlushedRegion() {
        BufferRegion r = lastFlushedRegion;
        lastFlushedRegion = null;
        return r;
    }

    /**
     * The current position counter of the parser. Increases by exactly one for each byte consumed
     * by {@link #feed}.
     *
     * @return The current position
     */
    public long position() {
        return position;
    }

    /**
     * Whether we are currently in the buffering state, i.e. there is a JSON node, but it's not
     * done yet or we can't know for sure that it's done (e.g. for numbers). This is used to flush
     * any remaining buffering data when EOF is reached.
     *
     * @return {@code true} if we are currently buffering
     */
    public boolean isBuffering() {
        return bufferStart != -1;
    }

    /**
     * If we are {@link #isBuffering() buffering}, the start {@link #position()} of the region that
     * is being buffered.
     *
     * @return The buffer region start
     * @throws IllegalStateException if we aren't buffering
     */
    public long bufferStart() {
        if (bufferStart == -1) {
            throw new IllegalStateException("Not buffering");
        }
        return bufferStart;
    }

    private static void failMismatchedBrackets() throws JsonSyntaxException {
        throw new JsonSyntaxException("JSON has mismatched brackets");
    }

    private static void failMissingWs() throws JsonSyntaxException {
        // we *could* support this, but jackson doesn't, and this makes the
        // implementation a little easier (we can do with returning a boolean)
        throw new JsonSyntaxException("After top-level scalars, there must be whitespace before the next node");
    }

    private static boolean ws(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    private enum State {
        /**
         * Default state, anything that's not inside a string, not a top-level scalar (numbers,
         * booleans, null), and not a special state for {@link #unwrapTopLevelArray() unwrapping}.
         */
        BASE,
        /**
         * State inside a string. Braces are ignored, and escape sequences get special handling.
         */
        STRING,
        /**
         * State inside a "top-level scalar", i.e. a boolean, number or {@code null} that is not
         * part of an array or object. These are a bit special because unlike strings, which
         * terminate on {@code "}, and structures, which terminate on a bracket, these terminate on
         * whitespace.
         */
        TOP_LEVEL_SCALAR,
        /**
         * State just after {@code \} inside a {@link #STRING}. The next byte is ignored, and then
         * we return to {@link #STRING} state.
         */
        ESCAPE,
        /**
         * Special state for {@link #unwrapTopLevelArray() unwrapping}, before the top-level array.
         * At this point we don't know if there is a top-level array that we need to unwrap or not.
         */
        BEFORE_UNWRAP_ARRAY,
        /**
         * Special state for {@link #unwrapTopLevelArray() unwrapping}, after the closing brace of
         * a top-level array. Any further tokens after this are an error.
         */
        AFTER_UNWRAP_ARRAY,
        /**
         * Special state for {@link #noTokenization()}. The input is not visited at all, we just
         * assume everything is part of one root-level token and buffer it all.
         */
        BUFFER_ALL,
    }

    /**
     * A region that contains a JSON node. Positions are relative to {@link #position()}.
     *
     * @param start First byte position of this node
     * @param end   Position after the last byte of this node (i.e. it's exclusive)
     */
    public record BufferRegion(long start, long end) {
    }
}
