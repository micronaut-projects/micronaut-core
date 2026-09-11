/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.http.server.util.PerSecondCache;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * DateTimeElement LogElement.
 * <p>
 * Unless the pattern prints a sub-second field, the formatted text only changes once per second
 * and is cached in a {@link PerSecondCache}. The element is shared by every access log instance
 * ({@link #copy()} returns {@code this}), so the cache is shared by all event loops.
 *
 * @author croudet
 * @since 2.0
 */
final class DateTimeElement implements LogElement {

    /**
     * The date/time marker.
     */
    public static final String DATE_TIME = "t";

    private static final String COMMON_LOG_PATTERN = "'['dd/MMM/yyyy:HH:mm:ss Z']'";

    private static final Set<Event> LAST_RESPONSE_EVENTS = Collections.unmodifiableSet(EnumSet.of(Event.ON_LAST_RESPONSE_WRITE));

    private final DateTimeFormatter formatter;
    private final Set<Event> events;
    @Nullable
    private final String dateFormat;
    /**
     * {@code null} when the pattern has a sub-second field, in which case every value is
     * formatted from the exact current time.
     */
    @Nullable
    private final PerSecondCache cache;

    /**
     * Create a DateTimeElement.
     *
     * @param dateFormat The date time format. DateTimeFormtter is used. The format can start with "begin:" or "end:"
     * If the format starts with begin: (default) the time is taken at the beginning of the request processing.
     * If it starts with end: it is the time when the log entry gets written, close to the end of the request processing.
     */
    DateTimeElement(@Nullable final String dateFormat) {
        boolean fromStart;
        String format;
        if (dateFormat == null) {
            format = COMMON_LOG_PATTERN;
            fromStart = true;
        } else {
            fromStart = ! dateFormat.startsWith("end:");
            if (dateFormat.startsWith("begin:")) {
                format = dateFormat.substring("begin:".length());
                fromStart = true;
            } else if (dateFormat.startsWith("end:")) {
                format = dateFormat.substring("end:".length());
                fromStart = false;
            } else {
                format = dateFormat;
            }
        }
        this.dateFormat = dateFormat;
        String[] formatSplit = format.split(",");
        if (formatSplit.length < 2) {
            formatter = DateTimeFormatter.ofPattern(format, Locale.US);
        } else {
            formatter = DateTimeFormatter.ofPattern(formatSplit[0], Locale.US).withZone(ZoneId.of(formatSplit[1].strip()));
        }
        cache = hasSubSecondField(formatSplit[0]) ? null : new PerSecondCache(this::formatSecond);
        events = fromStart ? Event.REQUEST_HEADERS_EVENTS : LAST_RESPONSE_EVENTS;
    }

    /**
     * Whether the pattern contains an unquoted fraction-of-second ({@code S}), nano-of-second
     * ({@code n}), nano-of-day ({@code N}) or milli-of-day ({@code A}) field. Every other pattern
     * letter is a function of the instant truncated to the second.
     *
     * @param pattern The {@link DateTimeFormatter} pattern
     * @return {@code true} if the output can change within a second
     */
    static boolean hasSubSecondField(String pattern) {
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && (c == 'S' || c == 'n' || c == 'N' || c == 'A')) {
                return true;
            }
        }
        return false;
    }

    private String formatSecond(long epochSecond) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault()).format(formatter);
    }

    private String now() {
        if (cache != null) {
            return cache.now();
        }
        return ZonedDateTime.now().format(formatter);
    }

    /**
     * The value this element produces for the given instant.
     *
     * @param epochMillis The instant, in milliseconds since the epoch
     * @return The formatted text
     */
    String value(long epochMillis) {
        if (cache != null) {
            return cache.get(epochMillis);
        }
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(formatter);
    }

    @Override
    public Set<Event> events() {
        return events;
    }

    @Override
    public String onRequestHeaders(@Nullable SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        if (events.contains(Event.ON_REQUEST_HEADERS)) {
            return now();
        } else {
            return ConstantElement.UNKNOWN_VALUE;
        }
    }

    @Override
    public String onLastResponseWrite(int contentSize) {
        if (events.contains(Event.ON_LAST_RESPONSE_WRITE)) {
            return now();
        } else {
            return ConstantElement.UNKNOWN_VALUE;
        }
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String toString() {
        return dateFormat == null ? '%' + DATE_TIME : "%{" + dateFormat + '}' + DATE_TIME;
    }
}
