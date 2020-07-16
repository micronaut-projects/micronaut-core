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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * DateTimeElement LogElement.
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
    private final String dateFormat;

    /**
     * Create a DateTimeElement.
     *
     * @param dateFormat The date time format. DateTimeFormtter is used. The format can start with "begin:" or "end:"
     * If the format starts with begin: (default) the time is taken at the beginning of the request processing.
     * If it starts with end: it is the time when the log entry gets written, close to the end of the request processing.
     */
    DateTimeElement(final String dateFormat) {
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
        formatter = DateTimeFormatter.ofPattern(format, Locale.US);
        events = fromStart ? Event.REQUEST_HEADERS_EVENTS : LAST_RESPONSE_EVENTS;
    }

    @Override
    public Set<Event> events() {
        return events;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        if (events.contains(Event.ON_REQUEST_HEADERS)) {
            return ZonedDateTime.now().format(formatter);
        } else {
            return ConstantElement.UNKNOWN_VALUE;
        }
    }

    @Override
    public String onLastResponseWrite(int contentSize) {
        if (events.contains(Event.ON_LAST_RESPONSE_WRITE)) {
            return ZonedDateTime.now().format(formatter);
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
