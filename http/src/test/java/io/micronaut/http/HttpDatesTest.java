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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * HTTP dates of RFC 9110 section 5.6.7.
 */
class HttpDatesTest {

    private static final ZoneId GMT = ZoneId.of("GMT");
    private static final ZonedDateTime SUNDAY = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, GMT);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 21, 0, 0, 0, 0, GMT);

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
    void aDateMoreThanFiftyYearsInTheFutureIsInThePast() {
        // the cutoff is 2076-10-21T00:00:00Z: a date of 1976 one second after it, not 2076
        ZonedDateTime beyond = ZonedDateTime.of(1976, 10, 21, 0, 0, 1, 0, GMT);
        assertEquals(beyond, HttpDates.parse(rfc850(beyond), NOW));
        ZonedDateTime november = ZonedDateTime.of(1976, 11, 6, 8, 49, 37, 0, GMT);
        assertEquals(november, HttpDates.parse(rfc850(november), NOW));
    }

    @Test
    void aDateAtMostFiftyYearsInTheFutureIsInTheFuture() {
        ZonedDateTime atCutoff = ZonedDateTime.of(2076, 10, 21, 0, 0, 0, 0, GMT);
        assertEquals(atCutoff, HttpDates.parse(rfc850(atCutoff), NOW));
        ZonedDateTime inside = ZonedDateTime.of(2076, 10, 20, 23, 59, 59, 0, GMT);
        assertEquals(inside, HttpDates.parse(rfc850(inside), NOW));
        ZonedDateTime past = ZonedDateTime.of(1994, 11, 6, 8, 49, 37, 0, GMT);
        assertEquals(past, HttpDates.parse(rfc850(past), NOW));
    }

    @Test
    void aDayOfTheWeekOfAnotherCenturyIsInvalid() {
        // 21-Nov-76 is a Sunday in 1976 and a Saturday in 2076, which is beyond the cutoff
        assertNull(HttpDates.parse("Saturday, 21-Nov-76 00:00:00 GMT", NOW));
        assertEquals(ZonedDateTime.of(1976, 11, 21, 0, 0, 0, 0, GMT),
            HttpDates.parse("Sunday, 21-Nov-76 00:00:00 GMT", NOW));
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

    private static String rfc850(ZonedDateTime date) {
        return DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.ENGLISH).format(date);
    }
}
