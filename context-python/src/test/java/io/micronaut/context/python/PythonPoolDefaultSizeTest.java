package io.micronaut.context.python;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default pool size scales down from the processor count and is capped.
 *
 * <p>More contexts cost throughput on any route reaching a Python bean that is not itself
 * pooled, so the previous {@code processors * 2} both overshot and grew the wrong way: a
 * larger machine made it worse. See micronaut-core#13553 for the measurements.
 */
class PythonPoolDefaultSizeTest {

    @Test
    void defaultSizeIsScaledDownAndCapped() {
        int[][] expectations = {
            // The floor holds for small machines: one context serialises every Python call.
            {1, 2}, {2, 2}, {4, 2}, {7, 2}, {8, 2},
            // processors / 4 through the middle. Twelve gives 3, which is where a 12-core
            // machine measured fastest.
            {12, 3}, {16, 4}, {24, 6},
            // The cap holds for large machines, so core count stops driving context count.
            {32, 8}, {64, 8}, {128, 8}, {256, 8}
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
        // processors * 2 on a 12-core machine was 24, which measured 847 req/s against
        // 1,240 at the new default of 3. Guards against a revert by arithmetic accident.
        assertEquals(3, PythonPool.defaultSizeForProcessors(12));
        assertEquals(8, PythonPool.defaultSizeForProcessors(96));
    }
}
