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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AsciiString;

/**
 * Sets the {@code Content-Length} header without going through a boxed number and a
 * {@link String}. The value is an {@link AsciiString}, which the HTTP/1.1 encoder copies directly
 * and which reads back as the same decimal string. Small lengths, the common case for API
 * responses, share one cached instance per value.
 *
 * @since 5.3.0
 */
@Internal
final class ContentLengthValues {
    /**
     * Lengths below this value are cached.
     */
    static final int CACHE_SIZE = 1024;

    /**
     * Lazily filled. {@link AsciiString} is immutable apart from its hash code and string caches,
     * which it computes idempotently, so a racy read of an element either sees {@code null} and
     * creates an equal instance, or sees a fully constructed one.
     */
    private static final AsciiString[] CACHE = new AsciiString[CACHE_SIZE];

    private ContentLengthValues() {
    }

    /**
     * Set the {@code Content-Length} header, replacing any existing value.
     *
     * @param headers The headers
     * @param length  The content length, not negative
     */
    static void set(HttpHeaders headers, long length) {
        headers.set(HttpHeaderNames.CONTENT_LENGTH, of(length));
    }

    /**
     * The header value for the given length.
     *
     * @param length The content length, not negative
     * @return The decimal representation
     */
    static AsciiString of(long length) {
        if (length >= 0 && length < CACHE_SIZE) {
            int i = (int) length;
            AsciiString cached = CACHE[i];
            if (cached == null) {
                cached = format(length);
                CACHE[i] = cached;
            }
            return cached;
        }
        return format(length);
    }

    private static AsciiString format(long length) {
        if (length < 0) {
            // not a valid length, keep the previous textual form
            return new AsciiString(Long.toString(length));
        }
        int digits = 1;
        for (long v = length / 10; v != 0; v /= 10) {
            digits++;
        }
        byte[] bytes = new byte[digits];
        long v = length;
        for (int i = digits - 1; i >= 0; i--) {
            bytes[i] = (byte) ('0' + (v % 10));
            v /= 10;
        }
        return new AsciiString(bytes, false);
    }
}
