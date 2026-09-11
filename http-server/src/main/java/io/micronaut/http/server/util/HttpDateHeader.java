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
package io.micronaut.http.server.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MutableHttpHeaders;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Produces the value of the {@code Date} response header (RFC 1123, GMT). The text only changes
 * once per second, so it is cached in a {@link PerSecondCache} shared by every response writer
 * instead of being formatted for each response.
 *
 * @since 5.2.0
 */
@Internal
public final class HttpDateHeader {
    private static final PerSecondCache CACHE = new PerSecondCache(HttpDateHeader::format);

    private HttpDateHeader() {
    }

    /**
     * The {@code Date} header value for the current wall-clock time.
     *
     * @return The RFC 1123 text
     */
    public static String now() {
        return CACHE.now();
    }

    /**
     * The {@code Date} header value for the given instant.
     *
     * @param epochMillis The instant, in milliseconds since the epoch
     * @return The RFC 1123 text
     */
    public static String get(long epochMillis) {
        return CACHE.get(epochMillis);
    }

    private static String format(long epochSecond) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(epochSecond).atZone(MutableHttpHeaders.GMT));
    }
}
