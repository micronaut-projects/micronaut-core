package io.micronaut.http.server.netty.jackson;

import io.micronaut.core.annotation.Internal;

@Internal
sealed class JsonCounter {
    int depth = 0;
    State state = State.BASE;

    private JsonCounter() {}

    public static JsonCounter create(boolean unwrapTopLevelArray) {
        return unwrapTopLevelArray ? new UnwrappingArray() : new JsonCounter();
    }

    public FeedResult feed(byte b) {
        if (b == 0) {
            // RFC 4627 allows JSON to be encoded as UTF-8, UTF-16 or UTF-32. It also specifies a
            // charset detection algorithm using 0x00 bytes.
            // Later standards (RFC 8259) only permit UTF-8, but Jackson still allows other
            // charsets. To avoid potential parser differential vulnerabilities, we forbid any 0x00
            // bytes in the input. They never appear in valid UTF-8 JSON.
            throw new IllegalArgumentException("Input must be legal UTF-8 JSON");
        }
        State oldState = state;
        switch (oldState) {
            case BASE, TOP_LEVEL_SCALAR -> {
                switch (b) {
                    case '{', '[' -> {
                        if (oldState == State.TOP_LEVEL_SCALAR) {
                            failMissingWs();
                        }
                        depth = Math.incrementExact(depth);
                    }
                    case '}', ']' -> {
                        if (depth == 0) {
                            throw new IllegalArgumentException("JSON has mismatched brackets");
                        }
                        if (--depth == 0) {
                            return FeedResult.FLUSH_AFTER;
                        }
                    }
                    case '"' -> {
                        if (oldState == State.TOP_LEVEL_SCALAR) {
                            failMissingWs();
                        }
                        state = State.STRING;
                    }
                    case ' ', '\n', '\r', '\t' -> {
                        if (oldState == State.TOP_LEVEL_SCALAR) {
                            state = State.BASE;
                            return FeedResult.FLUSH_AFTER;
                        }
                        return FeedResult.MAY_SKIP;
                    }
                    default -> {
                        if (depth == 0) {
                            if (b == (byte) 0xef) {
                                throw new IllegalArgumentException("Detected 0xef in input. This is likely a UTF-8 byte-order mark. This is not permitted.");
                            }
                            state = State.TOP_LEVEL_SCALAR;
                        }
                    }
                }
            }
            case ESCAPE -> state = State.STRING;
            case STRING -> {
                switch (b) {
                    case '\\' -> state = State.ESCAPE;
                    case '"' -> {
                        state = State.BASE;
                        if (depth == 0) {
                            return FeedResult.FLUSH_AFTER;
                        }
                    }
                }
            }
        }
        return FeedResult.BUFFER;
    }

    private static void failMissingWs() {
        // we *could* support this, but jackson doesn't, and this makes the
        // implementation a little easier (we can do with returning a boolean)
        throw new IllegalArgumentException("After top-level scalars, there must be whitespace before the next node");
    }

    private enum State {
        BASE,
        STRING,
        TOP_LEVEL_SCALAR,
        ESCAPE
    }

    public enum FeedResult {
        /**
         * The character <i>must</i> be skipped. This is only returned for the '[],' characters of
         * a top-level array if array unwrapping is enabled.
         */
        MUST_SKIP,
        /**
         * The character may be skipped. This is returned for optional whitespace.
         */
        MAY_SKIP,
        /**
         * The character is relevant and should be buffered.
         */
        BUFFER,
        /**
         * The character should be buffered, and it terminates the current JSON node, which is then
         * ready for parsing.
         */
        FLUSH_AFTER,
        /**
         * The last JSON node terminates <i>before</i> this character, and this character should be
         * skipped. Only returned for '],' inside a top-level array if array unwrapping is enabled.
         */
        FLUSH_BEFORE_AND_SKIP,
    }

    private static final class UnwrappingArray extends JsonCounter {
        private UnwrapState unwrapState = UnwrapState.BEFORE;
        private boolean firstValue = true;

        @Override
        public FeedResult feed(byte b) {
            return switch (unwrapState) {
                case NOT_AN_ARRAY -> super.feed(b);
                case BEFORE -> {
                    if (b == '[') {
                        unwrapState = UnwrapState.VALUE;
                        yield FeedResult.MUST_SKIP;
                    }

                    FeedResult superResult = super.feed(b);
                    if (superResult != FeedResult.MAY_SKIP) {
                        unwrapState = UnwrapState.NOT_AN_ARRAY;
                    }
                    yield superResult;
                }
                case VALUE -> {
                    if (b == ']' && firstValue) {
                        unwrapState = UnwrapState.DONE;
                        yield FeedResult.MUST_SKIP;
                    }
                    if (state == State.TOP_LEVEL_SCALAR && (b == ',' || b == ']')) {
                        // special case: if we are in a scalar, and see an array-relevant character,
                        // move into the COMMA_OR_END state and handle it there
                        state = State.BASE;
                        unwrapState = UnwrapState.COMMA_OR_END;
                        FeedResult actualResult = feed(b);
                        if (actualResult != FeedResult.MUST_SKIP) {
                            throw new AssertionError();
                        }
                        yield FeedResult.FLUSH_BEFORE_AND_SKIP;
                    }

                    FeedResult superResult = super.feed(b);
                    if (superResult == FeedResult.FLUSH_AFTER) {
                        unwrapState = UnwrapState.COMMA_OR_END;
                    }
                    yield superResult;
                }
                case COMMA_OR_END -> {
                    if (b == ',') {
                        unwrapState = UnwrapState.VALUE;
                        firstValue = false;
                        yield FeedResult.MUST_SKIP;
                    }
                    if (b == ']') {
                        unwrapState = UnwrapState.DONE;
                        yield FeedResult.MUST_SKIP;
                    }

                    FeedResult superResult = super.feed(b);
                    if (superResult != FeedResult.MAY_SKIP) {
                        throw new IllegalArgumentException("Syntax error in array");
                    }
                    yield superResult;
                }
                case DONE -> {
                    FeedResult superResult = super.feed(b);
                    if (superResult != FeedResult.MAY_SKIP) {
                        throw new IllegalArgumentException("Superfluous input after top-level array");
                    }
                    yield superResult;
                }
            };
        }

        private enum UnwrapState {
            BEFORE,
            NOT_AN_ARRAY,
            VALUE,
            COMMA_OR_END,
            DONE,
        }
    }
}
