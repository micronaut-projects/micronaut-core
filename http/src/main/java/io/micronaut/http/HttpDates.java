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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
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

    /**
     * The RFC 850 format without the day of the week, which is checked once the century of the
     * two-digit year is known.
     */
    private static final DateTimeFormatter RFC_850_DATE = new DateTimeFormatterBuilder()
        .appendPattern("dd-MMM-")
        .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
        .appendPattern(" HH:mm:ss 'GMT'")
        .toFormatter(Locale.ENGLISH)
        .withZone(GMT);

    private static final int FUTURE_YEARS = 50;
    private static final int CENTURY = 100;

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
        return parse(text, ZonedDateTime.now(GMT));
    }

    /**
     * Parse an HTTP date, see {@link #parse(CharSequence)}, relative to the given current time,
     * which decides the century of the two-digit year of an RFC 850 date.
     *
     * @param text The text
     * @param now  The current time
     * @return The date in GMT, or {@code null} if the text is not an HTTP date
     */
    static @Nullable ZonedDateTime parse(@Nullable CharSequence text, ZonedDateTime now) {
        if (text == null) {
            return null;
        }
        String value = text.toString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            int comma = value.indexOf(',');
            if (comma < 0) {
                return ZonedDateTime.from(ASCTIME.parse(value));
            }
            if (comma > 3) {
                return parseRfc850(value, comma, now);
            }
            return ZonedDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)).withZoneSameInstant(GMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parse an RFC 850 date. A date with a two-digit year that appears to be more than 50 years
     * in the future is in the most recent year in the past with the same last two digits
     * (RFC 9110, section 5.6.7).
     *
     * @param value The text
     * @param comma The index of the comma after the day of the week
     * @param now   The current time
     * @return The date, or {@code null} if the day of the week does not match it
     */
    private static @Nullable ZonedDateTime parseRfc850(String value, int comma, ZonedDateTime now) {
        ZonedDateTime cutoff = now.withZoneSameInstant(GMT).plusYears(FUTURE_YEARS);
        ZonedDateTime date = ZonedDateTime.from(RFC_850_DATE.parse(value.substring(comma + 1).trim()));
        // the latest year with the two digits that is not after the year of the cutoff
        int year = cutoff.getYear() - Math.floorMod(cutoff.getYear() - date.getYear(), CENTURY);
        date = date.withYear(year);
        if (date.isAfter(cutoff)) {
            date = date.minusYears(CENTURY);
        }
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return dayOfWeek.equals(value.substring(0, comma)) ? date : null;
    }
}
