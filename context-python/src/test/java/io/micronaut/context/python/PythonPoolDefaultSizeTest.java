package io.micronaut.context.python;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default pool size scales down from the processor count and is capped.
 *
 * <p>The previous {@code processors * 2} grew the wrong way: a larger machine made it worse.
 * How far down to scale depends on whether an application's Python services are pooled, which
 * is what this release makes possible — with them pooled, contexts add concurrency instead of
 * dividing the traffic that reaches an unpooled bean's single context, and the best size rises.
 * See micronaut-core#13553 for both sets of measurements.
 */
class PythonPoolDefaultSizeTest {

    @Test
    void defaultSizeIsScaledDownAndCapped() {
        int[][] expectations = {
            // The floor holds for small machines: one context serialises every Python call.
            {1, 2}, {2, 2}, {3, 2}, {4, 2},
            // processors / 2 through the middle. Twelve gives 6, the best compromise measured
            // on a 12-core machine: the only size within 25% of every scenario's own best.
            {6, 3}, {8, 4}, {12, 6}, {14, 7},
            // The cap holds for large machines, so core count stops driving context count.
            {16, 8}, {32, 8}, {64, 8}, {128, 8}, {256, 8}
        };
        for (int[] expectation : expectations) {
            int processors = expectation[0];
            assertEquals(
                expectation[1],
                PythonPool.defaultSizeForProcessors(processors),
                "wrong default for " + processors + " processors"
            );
        }
    }

    @Test
    void defaultSizeNeverLeavesTheBounds() {
        for (int processors = 1; processors <= 1024; processors++) {
            int size = PythonPool.defaultSizeForProcessors(processors);
            assertTrue(size >= 2, "pool size below the floor for " + processors + " processors");
            assertTrue(size <= 8, "pool size above the cap for " + processors + " processors");
        }
    }

    @Test
    void defaultSizeNeverDecreasesWithMoreProcessors() {
        int previous = 0;
        for (int processors = 1; processors <= 1024; processors++) {
            int size = PythonPool.defaultSizeForProcessors(processors);
            assertTrue(size >= previous, "pool size decreased at " + processors + " processors");
            previous = size;
        }
    }

    @Test
    void theOldDefaultIsNoLongerProduced() {
        // processors * 2 on a 12-core machine was 24. With the services pooled that measured
        // 1,423 req/s on a paged read against 2,205 at the default of 6, and 3,611 on a write
        // against 3,986 at 8. Guards against a revert by arithmetic accident.
        assertEquals(6, PythonPool.defaultSizeForProcessors(12));
        assertEquals(8, PythonPool.defaultSizeForProcessors(96));
    }
}
