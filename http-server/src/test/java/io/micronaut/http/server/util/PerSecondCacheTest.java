package io.micronaut.http.server.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerSecondCacheTest {
    @Test
    void sameSecondIsFormattedOnce() {
        AtomicInteger calls = new AtomicInteger();
        PerSecondCache cache = new PerSecondCache(second -> {
            calls.incrementAndGet();
            return "s" + second;
        });

        String first = cache.get(1_000);
        assertEquals("s1", first);
        assertSame(first, cache.get(1_000));
        assertSame(first, cache.get(1_500));
        assertSame(first, cache.get(1_999));
        assertEquals(1, calls.get());
    }

    @Test
    void valueChangesAtTheSecondBoundary() {
        AtomicInteger calls = new AtomicInteger();
        PerSecondCache cache = new PerSecondCache(second -> {
            calls.incrementAndGet();
            return "s" + second;
        });

        String before = cache.get(1_999);
        String after = cache.get(2_000);
        assertEquals("s1", before);
        assertEquals("s2", after);
        assertNotEquals(before, after);
        assertEquals(2, calls.get());

        // going back to an earlier second is not served from the cache either
        assertEquals("s1", cache.get(1_001));
        assertEquals(3, calls.get());
    }

    @Test
    void negativeInstantsRoundTowardsMinusInfinity() {
        PerSecondCache cache = new PerSecondCache(second -> "s" + second);
        assertEquals("s-1", cache.get(-1));
        assertEquals("s-1", cache.get(-1_000));
        assertEquals("s-2", cache.get(-1_001));
        assertEquals("s0", cache.get(0));
    }

    @Test
    void nowIsWithinTheCurrentSecond() {
        PerSecondCache cache = new PerSecondCache(second -> "s" + second);
        long before = Math.floorDiv(System.currentTimeMillis(), 1000);
        String now = cache.now();
        long after = Math.floorDiv(System.currentTimeMillis(), 1000);
        long second = Long.parseLong(now.substring(1));
        assertTrue(second >= before && second <= after, now + " not in [" + before + ", " + after + "]");
    }
}
