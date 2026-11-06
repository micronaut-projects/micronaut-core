package io.micronaut.http.server.netty.handler;

import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressorTest {
    private static final HttpCompressionStrategy STRATEGY = new HttpCompressionStrategy() {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean shouldCompress(HttpResponse response) {
            return true;
        }

        @Override
        public int getMaxZstdEncodeSize() {
            // netty rejects a zero size when zstd-jni is on the class path
            return 1 << 20;
        }
    };

    /**
     * Reference implementation: the previous split-based tokenizer. The in-place version must
     * pick the same algorithm for every header. The selection step follows the weights the same
     * way {@link Compressor#determineEncoding(java.util.Iterator, Set)} does.
     */
    private static Compressor.Algorithm referenceDetermineEncoding(List<String> headerValues, Set<Compressor.Algorithm> available) {
        boolean brotli = available.contains(Compressor.Algorithm.BR);
        boolean zstd = available.contains(Compressor.Algorithm.ZSTD);
        boolean snappy = available.contains(Compressor.Algorithm.SNAPPY);
        boolean gzip = available.contains(Compressor.Algorithm.GZIP);
        boolean deflate = available.contains(Compressor.Algorithm.DEFLATE);
        float starQ = -1.0f;
        float brQ = -1.0f;
        float zstdQ = -1.0f;
        float snappyQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String s : headerValues) {
            for (String encoding : s.split(",")) {
                float q = 1.0f;
                int equalsPos = encoding.indexOf('=');
                if (equalsPos != -1) {
                    try {
                        q = Float.parseFloat(encoding.substring(equalsPos + 1));
                    } catch (NumberFormatException e) {
                        q = 0.0f;
                    }
                }
                if (encoding.contains("*")) {
                    starQ = q;
                } else if (encoding.contains("br") && q > brQ) {
                    brQ = q;
                } else if (encoding.contains("zstd") && q > zstdQ) {
                    zstdQ = q;
                } else if (encoding.contains("snappy") && q > snappyQ) {
                    snappyQ = q;
                } else if (encoding.contains("gzip") && q > gzipQ) {
                    gzipQ = q;
                } else if (encoding.contains("deflate") && q > deflateQ) {
                    deflateQ = q;
                }
            }
        }
        // selection by weight (RFC 9110 12.4.2): q=0 is not acceptable, the order breaks ties
        float[] weights = {brQ, zstdQ, snappyQ, gzipQ, deflateQ};
        boolean[] enabled = {brotli, zstd, snappy, gzip, deflate};
        Compressor.Algorithm best = null;
        float bestQ = 0.0f;
        for (int i = 0; i < weights.length; i++) {
            float q = weights[i] == -1.0f ? starQ : weights[i];
            if (enabled[i] && q > bestQ) {
                best = Compressor.Algorithm.values()[i];
                bestQ = q;
            }
        }
        return best;
    }

    /**
     * Every header shape is checked against every subset of the five algorithms, so each of them
     * is selected and skipped somewhere in the run whatever the test class path offers.
     */
    @ParameterizedTest
    @MethodSource
    void determineEncodingMatchesTheSplitBasedTokenizer(List<String> headerValues) {
        Compressor.Algorithm[] all = Compressor.Algorithm.values();
        for (int mask = 0; mask < 1 << all.length; mask++) {
            Set<Compressor.Algorithm> available = EnumSet.noneOf(Compressor.Algorithm.class);
            for (int i = 0; i < all.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    available.add(all[i]);
                }
            }
            Compressor.Algorithm expected = referenceDetermineEncoding(headerValues, available);
            assertEquals(expected, Compressor.determineEncoding(headerValues.iterator(), available), headerValues + " available=" + available);
        }
    }

    @Test
    void everyAlgorithmIsSelectedForItsOwnName() {
        Set<Compressor.Algorithm> all = EnumSet.allOf(Compressor.Algorithm.class);
        assertEquals(Compressor.Algorithm.BR, Compressor.determineEncoding(List.of("br").iterator(), all));
        assertEquals(Compressor.Algorithm.ZSTD, Compressor.determineEncoding(List.of("zstd").iterator(), all));
        assertEquals(Compressor.Algorithm.SNAPPY, Compressor.determineEncoding(List.of("snappy").iterator(), all));
        assertEquals(Compressor.Algorithm.GZIP, Compressor.determineEncoding(List.of("gzip").iterator(), all));
        assertEquals(Compressor.Algorithm.DEFLATE, Compressor.determineEncoding(List.of("deflate").iterator(), all));
        assertNull(Compressor.determineEncoding(List.of("identity").iterator(), all));
        // an unavailable algorithm is skipped in favour of the next one the client accepts
        assertEquals(Compressor.Algorithm.GZIP, Compressor.determineEncoding(List.of("br, zstd, gzip").iterator(), EnumSet.of(Compressor.Algorithm.GZIP, Compressor.Algorithm.DEFLATE)));
        // the instance method uses what the compressor has options for: gzip, deflate and snappy always, brotli and zstd when available
        Compressor compressor = new Compressor(STRATEGY);
        assertEquals(Compressor.Algorithm.GZIP, compressor.determineEncoding(List.of("gzip").iterator()));
        assertEquals(Compressor.Algorithm.SNAPPY, compressor.determineEncoding(List.of("snappy").iterator()));
    }

    /**
     * An explicit {@code q=0} marks an encoding as not acceptable (RFC 9110 12.4.2), and the
     * encoding with the highest weight wins; the algorithm order only breaks ties.
     */
    @ParameterizedTest
    @MethodSource
    void determineEncodingHonoursWeights(String header, Compressor.Algorithm expected) {
        Set<Compressor.Algorithm> all = EnumSet.allOf(Compressor.Algorithm.class);
        assertEquals(expected, Compressor.determineEncoding(List.of(header).iterator(), all), header);
    }

    private static Stream<Arguments> determineEncodingHonoursWeights() {
        return Stream.of(
            // q=0 is never selected
            Arguments.of("br;q=0, gzip;q=1", Compressor.Algorithm.GZIP),
            Arguments.of("br;q=0, gzip", Compressor.Algorithm.GZIP),
            Arguments.of("zstd;q=0, gzip", Compressor.Algorithm.GZIP),
            Arguments.of("snappy;q=0, deflate", Compressor.Algorithm.DEFLATE),
            Arguments.of("gzip;q=0", null),
            Arguments.of("br;q=0, gzip;q=0", null),
            Arguments.of("br;q=0, *", Compressor.Algorithm.ZSTD),
            Arguments.of("br;q=0, zstd;q=0, snappy;q=0, gzip;q=0, deflate;q=0, *", null),
            // the highest weight wins, not only against the next algorithm in the order
            Arguments.of("br;q=0.1, gzip;q=1", Compressor.Algorithm.GZIP),
            Arguments.of("br;q=0.1, zstd;q=0.2, deflate;q=0.9", Compressor.Algorithm.DEFLATE),
            Arguments.of("zstd;q=0.5, deflate;q=0.6", Compressor.Algorithm.DEFLATE),
            Arguments.of("snappy;q=0.3, gzip;q=0.4", Compressor.Algorithm.GZIP),
            Arguments.of("deflate;q=0.5, gzip;q=1.0", Compressor.Algorithm.GZIP),
            Arguments.of("deflate;q=1.0, gzip;q=0.5", Compressor.Algorithm.DEFLATE),
            // ties keep the current preference: br, zstd, snappy, gzip, deflate
            Arguments.of("gzip, deflate, br", Compressor.Algorithm.BR),
            Arguments.of("deflate, gzip", Compressor.Algorithm.GZIP),
            Arguments.of("deflate;q=0.5, zstd;q=0.5, gzip;q=0.5", Compressor.Algorithm.ZSTD),
            Arguments.of("zstd;q=0.5, br;q=0.5", Compressor.Algorithm.BR),
            Arguments.of("snappy, zstd", Compressor.Algorithm.ZSTD),
            Arguments.of("*", Compressor.Algorithm.BR),
            Arguments.of("br;q=0.9, gzip;q=0.8, *;q=0.1", Compressor.Algorithm.BR)
        );
    }

    @Test
    void anUnavailableAlgorithmFallsBackToTheNextHighestWeight() {
        Set<Compressor.Algorithm> gzipAndDeflate = EnumSet.of(Compressor.Algorithm.GZIP, Compressor.Algorithm.DEFLATE);
        // deflate is preferred but unavailable, gzip is acceptable
        assertEquals(Compressor.Algorithm.GZIP, Compressor.determineEncoding(List.of("deflate;q=1.0, gzip;q=0.5").iterator(), EnumSet.of(Compressor.Algorithm.GZIP)));
        assertEquals(Compressor.Algorithm.DEFLATE, Compressor.determineEncoding(List.of("br;q=1, gzip;q=0.2, deflate;q=0.4").iterator(), gzipAndDeflate));
        assertNull(Compressor.determineEncoding(List.of("br, gzip;q=0").iterator(), gzipAndDeflate));
    }

    /**
     * The tokenizer must scale linearly with the header length: a quadratic scan of an 8 KiB
     * header of thousands of entries cost tens of milliseconds per request. The check compares the
     * cost of a header against one eight times as long, so it does not depend on machine speed:
     * linear growth gives a ratio near 8, quadratic growth a ratio near 64.
     */
    @ParameterizedTest
    @ValueSource(strings = {",", "a,", "x;q,", "gzip;q=0.5,"})
    void longHeadersAreTokenizedInLinearTime(String entry) {
        Set<Compressor.Algorithm> all = EnumSet.allOf(Compressor.Algorithm.class);
        int shortRepeats = 1000 / entry.length();
        // fits the default maxHeaderSize of 8192
        String shortHeader = entry.repeat(shortRepeats);
        String longHeader = entry.repeat(shortRepeats * 8);
        // interleave the two so that JIT and GC affect both alike; take the fastest round for each
        long shortBest = Long.MAX_VALUE;
        long longBest = Long.MAX_VALUE;
        for (int round = 0; round < 10; round++) {
            shortBest = Math.min(shortBest, timePerCall(shortHeader, all));
            longBest = Math.min(longBest, timePerCall(longHeader, all));
        }
        double ratio = (double) longBest / Math.max(shortBest, 1);
        assertTrue(ratio < 24, "tokenizing " + longHeader.length() + " chars took " + ratio + " times as long as " + shortHeader.length() + " chars");
        assertEquals(referenceDetermineEncoding(List.of(longHeader), all),
            Compressor.determineEncoding(List.of(longHeader).iterator(), all));
    }

    private static long timePerCall(String header, Set<Compressor.Algorithm> available) {
        List<String> values = List.of(header);
        for (int i = 0; i < 20; i++) {
            Compressor.determineEncoding(values.iterator(), available);
        }
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            Compressor.determineEncoding(values.iterator(), available);
        }
        return (System.nanoTime() - start) / 50;
    }

    private static Stream<Arguments> determineEncodingMatchesTheSplitBasedTokenizer() {
        return Stream.of(
            List.of(),
            List.of(""),
            List.of("gzip"),
            List.of("gzip, deflate"),
            List.of("gzip,deflate,br"),
            List.of("deflate;q=0.5, gzip;q=1.0"),
            List.of("deflate;q=1.0, gzip;q=0.5"),
            List.of("gzip;q=0"),
            List.of("gzip;q=0, deflate"),
            List.of("gzip;q=abc, deflate"),
            List.of("identity"),
            List.of("identity;q=1.0, *;q=0"),
            List.of("*"),
            List.of("*;q=0.1"),
            List.of("br;q=0.9, gzip;q=0.8, *;q=0.1"),
            List.of("snappy, zstd"),
            List.of("zstd;q=0.5, br;q=0.5"),
            List.of("gzip,"),
            List.of(",gzip"),
            List.of("gzip;q=1,,deflate"),
            List.of("gzip;q=0.5=x"),
            List.of("=gzip"),
            List.of("gzip=", "deflate"),
            List.of("gzip", "deflate;q=0.5"),
            List.of("gzip;q=0.1", "gzip;q=0.9"),
            List.of("x-gzip"),
            List.of("brotli"),
            List.of(" gzip ; q = 0.5 ")
        ).map(Arguments::of);
    }
}
