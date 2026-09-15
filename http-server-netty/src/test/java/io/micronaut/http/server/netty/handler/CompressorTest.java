package io.micronaut.http.server.netty.handler;

import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            return 0;
        }
    };

    /**
     * Reference implementation: the previous split-based tokenizer. The in-place version must
     * pick the same algorithm for every header.
     */
    private static Compressor.Algorithm referenceDetermineEncoding(List<String> headerValues, boolean brotli, boolean zstd) {
        float starQ = -1.0f;
        float brQ = -1.0f;
        float zstdQ = -1.0f;
        float snappyQ = -1.0f;
        float gzipQ = -1.0f;
        float deflateQ = -1.0f;
        for (String s : headerValues) {
            for (String encoding : Arrays.asList(s.split(","))) {
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
        if (brQ > 0.0f || zstdQ > 0.0f || snappyQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ != -1.0f && brQ >= zstdQ && brotli) {
                return Compressor.Algorithm.BR;
            } else if (zstdQ != -1.0f && zstdQ >= snappyQ && zstd) {
                return Compressor.Algorithm.ZSTD;
            } else if (snappyQ != -1.0f && snappyQ >= gzipQ) {
                return Compressor.Algorithm.SNAPPY;
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ) {
                return Compressor.Algorithm.GZIP;
            } else if (deflateQ != -1.0f) {
                return Compressor.Algorithm.DEFLATE;
            }
        }
        if (starQ > 0.0f) {
            if (brQ == -1.0f && brotli) {
                return Compressor.Algorithm.BR;
            }
            if (zstdQ == -1.0f && zstd) {
                return Compressor.Algorithm.ZSTD;
            }
            if (snappyQ == -1.0f) {
                return Compressor.Algorithm.SNAPPY;
            }
            if (gzipQ == -1.0f) {
                return Compressor.Algorithm.GZIP;
            }
            if (deflateQ == -1.0f) {
                return Compressor.Algorithm.DEFLATE;
            }
        }
        return null;
    }

    @ParameterizedTest
    @MethodSource
    void determineEncodingMatchesTheSplitBasedTokenizer(List<String> headerValues) {
        Compressor compressor = new Compressor(STRATEGY);
        Compressor.Algorithm expected = referenceDetermineEncoding(headerValues, Brotli.isAvailable(), Zstd.isAvailable());
        assertEquals(expected, compressor.determineEncoding(headerValues.iterator()), headerValues.toString());
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
