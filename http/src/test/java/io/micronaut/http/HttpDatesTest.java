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

import io.micronaut.http.simple.SimpleHttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HTTP dates of RFC 9110 section 5.6.7.
 */
class HttpDatesTest {

    private static final ZonedDateTime SUNDAY = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, ZoneId.of("GMT"));

    @Test
    void formatsTheImfFixdateWithATwoDigitDay() {
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", HttpDates.format(Instant.EPOCH));
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", HttpDates.format(SUNDAY));
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", HttpDates.format(SUNDAY.withZoneSameInstant(ZoneId.of("Europe/Prague"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Sun, 06 Nov 1994 08:49:37 GMT",
        "Sun, 6 Nov 1994 08:49:37 GMT",
        "Sunday, 06-Nov-94 08:49:37 GMT",
        "Sun Nov  6 08:49:37 1994",
        " Sun, 06 Nov 1994 08:49:37 GMT "
    })
    void parsesTheFormatsARecipientMustAccept(String text) {
        assertEquals(SUNDAY, HttpDates.parse(text));
    }

    @Test
    void parsesAnOffsetToGmt() {
        assertEquals(SUNDAY, HttpDates.parse("Sun, 06 Nov 1994 09:49:37 +0100"));
    }

    @Test
    void aTwoDigitYearMoreThanFiftyYearsInTheFutureIsInThePast() {
        int year = LocalDate.now(ZoneId.of("GMT")).getYear();
        int future = year + 51;
        ZonedDateTime parsed = HttpDates.parse(rfc850(future - 100));
        assertEquals(future - 100, parsed.getYear());
        int near = year + 10;
        assertEquals(near, HttpDates.parse(rfc850(near)).getYear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "yesterday", "Mon, 06 Nov 1994 08:49:37 GMT", "Sun Nov 6 08:49:37"})
    void anInvalidDateIsNull(String text) {
        assertNull(HttpDates.parse(text));
    }

    @Test
    void headersWriteAndReadHttpDates() {
        MutableHttpHeaders headers = new SimpleHttpHeaders();
        headers.lastModified(0L);
        headers.expires(0L);
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", headers.get(HttpHeaders.LAST_MODIFIED));
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", headers.get(HttpHeaders.EXPIRES));

        headers.set(HttpHeaders.IF_MODIFIED_SINCE, "Sunday, 06-Nov-94 08:49:37 GMT");
        assertEquals(SUNDAY, headers.getDate(HttpHeaders.IF_MODIFIED_SINCE));
        headers.set(HttpHeaders.IF_MODIFIED_SINCE, "Sun Nov  6 08:49:37 1994");
        assertEquals(SUNDAY, headers.getDate(HttpHeaders.IF_MODIFIED_SINCE));
        headers.set(HttpHeaders.IF_MODIFIED_SINCE, "not a date");
        assertNull(headers.getDate(HttpHeaders.IF_MODIFIED_SINCE));
    }

    private static String rfc850(int year) {
        ZonedDateTime date = ZonedDateTime.of(year, 1, 2, 3, 4, 5, 0, ZoneId.of("GMT"));
        return date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
            + String.format(java.util.Locale.ENGLISH, ", 02-Jan-%02d 03:04:05 GMT", year % 100);
    }
}
