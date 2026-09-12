package io.micronaut.http.server.util;

import io.micronaut.http.MutableHttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDateHeaderTest {
    /**
     * The value {@link MutableHttpHeaders#date(java.time.LocalDateTime)} produced before the
     * cache was introduced, for the given instant.
     */
    private static String legacy(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .withZoneSameInstant(MutableHttpHeaders.GMT)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 999L, 1_000L, 951_782_400_123L, 1_700_000_000_999L, 4_102_444_799_000L})
    void matchesTheUncachedFormat(long epochMillis) {
        assertEquals(legacy(epochMillis), HttpDateHeader.get(epochMillis));
    }

    @Test
    void fixedValues() {
        assertEquals("Thu, 1 Jan 1970 00:00:00 GMT", HttpDateHeader.get(0));
        assertEquals("Tue, 14 Nov 2023 22:13:20 GMT", HttpDateHeader.get(1_700_000_000_000L));
        assertEquals("Tue, 14 Nov 2023 22:13:20 GMT", HttpDateHeader.get(1_700_000_000_999L));
        assertEquals("Tue, 14 Nov 2023 22:13:21 GMT", HttpDateHeader.get(1_700_000_001_000L));
    }

    @Test
    void sameSecondReturnsTheCachedInstance() {
        String first = HttpDateHeader.get(1_700_000_000_000L);
        assertSame(first, HttpDateHeader.get(1_700_000_000_500L));
        assertSame(first, HttpDateHeader.get(1_700_000_000_999L));
    }

    @Test
    void changesAtTheSecondBoundary() {
        String before = HttpDateHeader.get(1_700_000_000_999L);
        String after = HttpDateHeader.get(1_700_000_001_000L);
        assertNotEquals(before, after);
        assertEquals(legacy(1_700_000_000_999L), before);
        assertEquals(legacy(1_700_000_001_000L), after);
    }

    @Test
    void nowParsesToTheCurrentTime() {
        long before = System.currentTimeMillis() / 1000;
        String now = HttpDateHeader.now();
        long after = System.currentTimeMillis() / 1000;
        long parsed = ZonedDateTime.parse(now, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
        assertTrue(parsed >= before && parsed <= after, now);
    }
}
