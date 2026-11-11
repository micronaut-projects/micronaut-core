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
package io.micronaut.http.server.netty.handler;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Accept-Encoding header of thousands of entries with an empty weight ({@code =,=,=,...})
 * must not cost much more to parse than a header of the same length with valid weights. Before
 * the fix every malformed weight threw and caught a NumberFormatException.
 */
class CompressorMalformedWeightCostTest {

    private static final Set<Compressor.Algorithm> AVAILABLE = EnumSet.of(Compressor.Algorithm.GZIP, Compressor.Algorithm.DEFLATE);

    @Test
    void malformedWeightsAreNotMuchSlowerThanValidWeights() {
        String malformed = "=,".repeat(3900);
        String valid = "=1,".repeat(2600);
        // warm up both paths
        long sink = run(malformed, 300) + run(valid, 300);
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long malformedNanos = run(malformed, 200);
            long validNanos = run(valid, 200);
            best = Math.min(best, malformedNanos / Math.max(1, validNanos));
            sink += malformedNanos;
        }
        assertTrue(sink != 0);
        assertTrue(best < 5, "malformed Accept-Encoding weights cost " + best + "x a valid header of the same length");
    }

    private static long run(String header, int iterations) {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Compressor.determineEncoding(List.of(header).iterator(), AVAILABLE);
        }
        return System.nanoTime() - start;
    }
}
