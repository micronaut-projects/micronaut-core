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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Formats and parses HTTP dates (RFC 9110, section 5.6.7): a date is sent in the IMF-fixdate
 * format, e.g. {@code Sun, 06 Nov 1994 08:49:37 GMT}, and the obsolete RFC 850
 * ({@code Sunday, 06-Nov-94 08:49:37 GMT}) and asctime ({@code Sun Nov  6 08:49:37 1994}) formats
 * are accepted as well.
 *
 * @since 5.3.0
 */
@Internal
public final class HttpDates {

    private static final ZoneId GMT = ZoneId.of("GMT");

    /**
     * The IMF-fixdate format: unlike {@link DateTimeFormatter#RFC_1123_DATE_TIME}, the day of
     * the month always has two digits.
     */
    private static final DateTimeFormatter IMF_FIXDATE = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        .withZone(GMT);

    private static final DateTimeFormatter ASCTIME = DateTimeFormatter
        .ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.ENGLISH)
        .withZone(GMT);

    private HttpDates() {
    }

    /**
     * Format a date in the IMF-fixdate format, in GMT.
     *
     * @param date The date
     * @return The text, e.g. {@code Thu, 01 Jan 1970 00:00:00 GMT}
     */
    public static String format(TemporalAccessor date) {
        return IMF_FIXDATE.format(date);
    }

    /**
     * Parse an HTTP date in the IMF-fixdate, RFC 850 or asctime format. The IMF-fixdate is parsed
     * leniently as an RFC 1123 date: a one-digit day of the month and a numeric offset are
     * accepted too.
     *
     * @param text The text
     * @return The date in GMT, or {@code null} if the text is not an HTTP date
     */
    public static @Nullable ZonedDateTime parse(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        String value = text.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            DateTimeFormatter formatter;
            int comma = value.indexOf(',');
            if (comma < 0) {
                formatter = ASCTIME;
            } else if (comma > 3) {
                formatter = rfc850();
            } else {
                formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            }
            return ZonedDateTime.from(formatter.parse(value)).withZoneSameInstant(GMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * The RFC 850 format. A two-digit year that appears to be more than 50 years in the future
     * is the most recent year in the past with the same last two digits.
     *
     * @return The formatter
     */
    private static DateTimeFormatter rfc850() {
        return new DateTimeFormatterBuilder()
            .appendPattern("EEEE, dd-MMM-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, LocalDate.now(GMT).minusYears(49))
            .appendPattern(" HH:mm:ss 'GMT'")
            .toFormatter(Locale.ENGLISH)
            .withZone(GMT);
    }
}
