/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.web.router.uri;

import io.micronaut.core.annotation.Internal;

import java.util.BitSet;
import java.util.Locale;

/**
 * Utility class for different URL percent encoding sets.
 *
 * @since 4.9.0
 * @author Jonas Konrad
 */
@Internal
final class PercentEncoder {
    static final PercentEncoder C0 = new PercentEncoder(new BitSet());

    static {
        for (char c = 0x20; c <= 0x7e; c++) {
            C0.keepSet.set(c);
        }
    }

    // whatwg sets
    static final PercentEncoder FRAGMENT = C0.addEncode(' ', '"', '<', '>', '`');
    static final PercentEncoder QUERY = C0.addEncode(' ', '"', '<', '>', '#');
    static final PercentEncoder SPECIAL_QUERY = QUERY.addEncode('\'');
    static final PercentEncoder PATH = QUERY.addEncode('?', '`', '{', '}');
    static final PercentEncoder USERINFO = PATH.addEncode('/', ':', ';', '=', '@', '|').addEncodeRange('[', '^');
    static final PercentEncoder COMPONENT = USERINFO.addEncode('+', ',').addEncodeRange('$', '&');
    static final PercentEncoder FORM = COMPONENT.addEncode('!', '~').addEncodeRange('\'', ')');

    // RFC 3986 (URI) sets
    static final PercentEncoder RFC3986_UNRESERVED = new PercentEncoder(new BitSet())
        .removeEncodeRange('a', 'z')
        .removeEncodeRange('A', 'Z')
        .removeEncodeRange('0', '9')
        .removeEncode('-', '.', '_', '~');
    static final PercentEncoder RFC3986_PCHAR = RFC3986_UNRESERVED.removeEncode('%', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', '@'); // ':' is allowed but makes java.net.URI hiccup
    static final PercentEncoder RFC3986_QUERY_CHAR = RFC3986_PCHAR.removeEncode('/', '?');

    private final BitSet keepSet;

    private PercentEncoder(BitSet keepSet) {
        this.keepSet = keepSet;
    }

    public void encodeByte(StringBuilder target, byte b) {
        if (keep(b)) {
            target.append((char) (b & 0xff));
        } else {
            target.ensureCapacity(target.length() + 3);
            appendEncodedByte(target, b);
        }
    }

    public boolean keep(byte b) {
        return keepSet.get(b & 0xff);
    }

    public void encodeUtf8(StringBuilder target, int codePoint) {
        if (codePoint < 0x80) {
            if (keepSet.get(codePoint)) {
                target.append((char) codePoint);
            } else {
                target.ensureCapacity(target.length() + 3);
                appendEncodedByte(target, (byte) codePoint);
            }
        } else if (codePoint < 0x800) {
            target.ensureCapacity(target.length() + 6);
            appendEncodedByte(target, (byte) (0b11000000 | (codePoint >> 6)));
            appendEncodedByte(target, (byte) (0b10000000 | (codePoint & 0b111111)));
        } else if (codePoint < 0x10000) {
            target.ensureCapacity(target.length() + 9);
            appendEncodedByte(target, (byte) (0b11100000 | (codePoint >> 12)));
            appendEncodedByte(target, (byte) (0b10000000 | ((codePoint >> 6) & 0b111111)));
            appendEncodedByte(target, (byte) (0b10000000 | (codePoint & 0b111111)));
        } else if (codePoint < 0x110000) {
            target.ensureCapacity(target.length() + 12);
            appendEncodedByte(target, (byte) (0b11110000 | (codePoint >> 18)));
            appendEncodedByte(target, (byte) (0b10000000 | ((codePoint >> 12) & 0b111111)));
            appendEncodedByte(target, (byte) (0b10000000 | ((codePoint >> 6) & 0b111111)));
            appendEncodedByte(target, (byte) (0b10000000 | (codePoint & 0b111111)));
        } else {
            throw new IllegalArgumentException("Code point out of range: " + codePoint);
        }
    }

    static void appendEncodedByte(StringBuilder target, byte b) {
        target.append('%');
        if ((b & 0xff) < 0x10) {
            target.append('0');
        }
        target.append(Integer.toHexString(b & 0xFF).toUpperCase(Locale.ROOT));
    }

    private PercentEncoder addEncode(char... removed) {
        BitSet result = (BitSet) keepSet.clone();
        for (char c : removed) {
            result.clear(c);
        }
        return new PercentEncoder(result);
    }

    private PercentEncoder addEncodeRange(char fromInclusive, char toExclusive) {
        BitSet result = (BitSet) keepSet.clone();
        for (char c = fromInclusive; c <= toExclusive; c++) {
            result.clear(c);
        }
        return new PercentEncoder(result);
    }

    private PercentEncoder removeEncode(char... removed) {
        BitSet result = (BitSet) keepSet.clone();
        for (char c : removed) {
            result.set(c);
        }
        return new PercentEncoder(result);
    }

    private PercentEncoder removeEncodeRange(char fromInclusive, char toExclusive) {
        BitSet result = (BitSet) keepSet.clone();
        for (char c = fromInclusive; c <= toExclusive; c++) {
            result.set(c);
        }
        return new PercentEncoder(result);
    }
}
