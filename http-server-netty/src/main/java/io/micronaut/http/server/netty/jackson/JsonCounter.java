package io.micronaut.http.server.netty.jackson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.netty.buffer.ByteBuf;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
@Internal
public final class JsonCounter {
    private long position;
    private int depth;
    private State state = State.BASE;

    private long bufferStart = -1;

    private boolean unwrappingArray;
    private boolean allowUnwrappingArrayComma;

    @Nullable
    private BufferRegion lastFlushedRegion;

    public void feed(ByteBuf buf) {
        if (!isBuffering()) {
            proceedUntilBuffering(buf);
        }
        if (isBuffering()) {
            proceedUntilNonBuffering(buf);
        }
    }

    public void unwrapTopLevelArray() {
        if (position != 0) {
            throw new IllegalStateException("Already consumed input");
        }
        state = State.BEFORE_UNWRAP_ARRAY;
    }

    private void proceedUntilNonBuffering(ByteBuf buf) {
        if (buf.hasArray()) {
            int arrayOffset = buf.arrayOffset();
            int newEnd = proceedUntilNonBuffering(buf.array(), arrayOffset + buf.readerIndex(), arrayOffset + buf.writerIndex());
            buf.readerIndex(newEnd - arrayOffset);
            return;
        }

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
            } else {
                throw new AssertionError(state);
            }
        }
        buf.readerIndex(i);
    }

    private int proceedUntilNonBuffering(byte[] array, int start, int end) {
        assert isBuffering();

        int i = start;
        while (i < end && bufferStart != -1) {
            start = i;
            if (state == State.BASE) {
                assert depth > 0 : depth;
                for (; i < end; i++) {
                    if (!skipBufferingBase(array[i])) {
                        break;
                    }
                }
                this.position += i - start;
                if (i < end) {
                    handleBufferingBaseSpecial(array[i]);
                    i++;
                    position++;
                }
            } else if (state == State.STRING) {
                for (; i < end; i++) {
                    if (!skipString(array[i])) {
                        break;
                    }
                }
                this.position += i - start;
                if (i < end) {
                    handleStringSpecial(array[i]);
                    i++;
                    position++;
                }
            } else if (state == State.ESCAPE) {
                handleEscape(array[i]);
                i++;
                position++;
            } else if (state == State.TOP_LEVEL_SCALAR) {
                assert depth == 0 : depth;
                for (; i < end; i++) {
                    if (!skipTopLevelScalar(array[i])) {
                        break;
                    }
                }
                this.position += i - start;
                if (i < end) {
                    handleTopLevelScalarSpecial(array[i]);
                    i++;
                    position++;
                }
            }
        }
        return i;
    }

    private void proceedUntilBuffering(ByteBuf buf) {
        assert !isBuffering();
        assert depth == 0 : depth;

        int start = buf.readerIndex();
        int end = buf.writerIndex();
        int i = start;

        if (state == State.AFTER_UNWRAP_ARRAY) {
            // top-level array consumed. reject further data
            skipWs(buf, i, end);
            if (i < end) {
                throw new IllegalArgumentException("Superfluous data after top-level array in streaming mode");
            }
        } else {
            // normal path
            assert state == State.BASE || state == State.BEFORE_UNWRAP_ARRAY : state;

            if (position == 0 && i < end && buf.getByte(i) == (byte) 0xef) {
                throw new IllegalArgumentException("UTF-8 BOM not allowed");
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

    private static int skipWs(ByteBuf buf, int i, int end) {
        for (; i < end; i++) {
            if (!ws(buf.getByte(i))) {
                break;
            }
        }
        return i;
    }

    private void handleNonBufferingBase(byte b) {
        switch (b) {
            case 0 -> failNul();
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

    private static boolean skipTopLevelScalar(byte b) {
        return !ws(b) && b != '"' && b != '{' && b != '[' && b != ']' && b != '}' && b != ',' && b != 0;
    }

    private void handleTopLevelScalarSpecial(byte b) {
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
            if (b == 0) {
                failNul();
            } else {
                failMissingWs();
            }
        }
    }

    private void handleEscape(byte b) {
        if (b == 0) {
            failNul();
        }
        state = State.STRING;
    }

    private static boolean skipString(byte b) {
        return b != '"' && b != '\\' && b != 0;
    }

    private void handleStringSpecial(byte b) {
        switch (b) {
            case 0 -> failNul();
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

    private static boolean skipBufferingBase(byte b) {
        return (b != '"') & (b != '{') & (b != '[') & (b != ']') & (b != '}') & (b != 0);
    }

    private void handleBufferingBaseSpecial(byte b) {
        switch (b) {
            case 0 -> failNul();
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

    public BufferRegion pollFlushedRegion() {
        BufferRegion r = lastFlushedRegion;
        lastFlushedRegion = null;
        return r;
    }

    public long position() {
        return position;
    }

    public boolean isBuffering() {
        return bufferStart != -1;
    }

    public long bufferStart() {
        if (bufferStart == -1) {
            throw new IllegalStateException("Not buffering");
        }
        return bufferStart;
    }

    private static void failNul() {
        // RFC 4627 allows JSON to be encoded as UTF-8, UTF-16 or UTF-32. It also specifies a
        // charset detection algorithm using 0x00 bytes.
        // Later standards (RFC 8259) only permit UTF-8, but Jackson still allows other
        // charsets. To avoid potential parser differential vulnerabilities, we forbid any 0x00
        // bytes in the input. They never appear in valid UTF-8 JSON.
        throw new IllegalArgumentException("Input must be legal UTF-8 JSON");
    }

    private static void failMismatchedBrackets() {
        throw new IllegalArgumentException("JSON has mismatched brackets");
    }

    private static void failMissingWs() {
        // we *could* support this, but jackson doesn't, and this makes the
        // implementation a little easier (we can do with returning a boolean)
        throw new IllegalArgumentException("After top-level scalars, there must be whitespace before the next node");
    }

    private static boolean ws(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }

    private enum State {
        BASE,
        STRING,
        TOP_LEVEL_SCALAR,
        ESCAPE,
        BEFORE_UNWRAP_ARRAY,
        AFTER_UNWRAP_ARRAY,
    }

    public record BufferRegion(long start, long end) {}
}
