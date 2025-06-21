package io.micronaut.web.router.uri;

import io.micronaut.core.annotation.Nullable;

final class PercentDecoder {
    private static final short EOF = -1;
    private static final short INVALID = -2;

    static void decode(StringBuilder dest, StringBuilder in, @Nullable Runnable onError) {
        outer:
        for (int i = 0; i < in.length(); i++) {
            short s = takeByte(in, i);
            if (s < 0) {
                dest.append(in.charAt(i));
                continue;
            }
            if (s < 0x80) {
                dest.append((char) s);
                i += 2;
            } else {
                int n;
                int cp;
                int min; // for overlong encoding detection
                if ((s & 0b11100000) == 0b11000000) {
                    n = 2;
                    cp = s & 0b11111;
                    min = 0b1_0000000;
                } else if ((s & 0b11110000) == 0b11100000) {
                    n = 3;
                    cp = s & 0b1111;
                    min = 0b1_00000_000000;
                } else if ((s & 0b11111000) == 0b11110000) {
                    n = 4;
                    cp = s & 0b111;
                    min = 0b1_0000_000000_000000;
                } else {
                    if (onError != null) {
                        onError.run();
                    }
                    dest.appendCodePoint(0xfffd);
                    continue;
                }
                for (int j = 1; j < n; j++) {
                    short then = takeByte(in, i + j * 3);
                    if (then < 0 || (then & 0b11000000) != 0b10000000) {
                        if (onError != null) {
                            onError.run();
                        }
                        dest.appendCodePoint(0xfffd);
                        continue outer;
                    }
                    cp = cp * 0b1000000 + (then & 0b111111);
                }
                if (cp > Character.MAX_CODE_POINT || cp < min) {
                    if (onError != null) {
                        onError.run();
                    }
                    dest.appendCodePoint(0xfffd);
                    continue;
                }
                dest.appendCodePoint(cp);
                i += n * 3 - 1;
            }
        }
    }

    private static short takeByte(CharSequence sequence, int index) {
        if (index + 2 >= sequence.length()) {
            return EOF;
        } else if (sequence.charAt(index) != '%') {
            return INVALID;
        }
        char hi = sequence.charAt(index + 1);
        char lo = sequence.charAt(index + 2);
        if (!WhatwgParser.isAsciiHexDigit(hi) || !WhatwgParser.isAsciiHexDigit(lo)) {
            return INVALID;
        }
        return Short.parseShort(sequence.subSequence(index + 1, index + 3).toString(), 16);
    }

    enum FailureMode {
        REPLACEMENT,
        FATAL,
    }
}
