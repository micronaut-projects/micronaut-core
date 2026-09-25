package io.micronaut.http.server.netty.handler;

import io.micronaut.http.MediaType;
import io.micronaut.http.server.netty.DefaultHttpCompressionStrategy;
import io.micronaut.http.server.netty.HttpCompressionStrategy;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
     * pick the same algorithm for every header.
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
        if (brQ > 0.0f || zstdQ > 0.0f || snappyQ > 0.0f || gzipQ > 0.0f || deflateQ > 0.0f) {
            if (brQ != -1.0f && brQ >= zstdQ && brotli) {
                return Compressor.Algorithm.BR;
            } else if (zstdQ != -1.0f && zstdQ >= snappyQ && zstd) {
                return Compressor.Algorithm.ZSTD;
            } else if (snappyQ != -1.0f && snappyQ >= gzipQ && snappy) {
                return Compressor.Algorithm.SNAPPY;
            } else if (gzipQ != -1.0f && gzipQ >= deflateQ && gzip) {
                return Compressor.Algorithm.GZIP;
            } else if (deflateQ != -1.0f && deflate) {
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
            if (snappyQ == -1.0f && snappy) {
                return Compressor.Algorithm.SNAPPY;
            }
            if (gzipQ == -1.0f && gzip) {
                return Compressor.Algorithm.GZIP;
            }
            if (deflateQ == -1.0f && deflate) {
                return Compressor.Algorithm.DEFLATE;
            }
        }
        return null;
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

    private static final List<String> ACCEPT_ENCODINGS = Arrays.asList(null, "", "gzip", "deflate", "br", "zstd", "snappy",
        "identity", "identity;q=1.0, *;q=0", "*", "gzip;q=0", "gzip, deflate, br, zstd", "deflate;q=0.5, gzip;q=1.0");
    private static final List<String> CONTENT_TYPES = Arrays.asList(null, "text/plain", "application/json",
        "application/json;charset=utf-8", "text/html; charset=UTF-8", "image/png", "application/octet-stream", "not a media type");
    private static final long[] CONTENT_LENGTHS = {-1, 0, 9, 10, 11, 10_000};

    private static DefaultHttpCompressionStrategy defaultStrategy(int threshold) throws Exception {
        Constructor<DefaultHttpCompressionStrategy> constructor = DefaultHttpCompressionStrategy.class.getDeclaredConstructor(int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(threshold, 6, 1 << 20);
    }

    private static ChannelHandlerContext context() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        return channel.pipeline().firstContext();
    }

    /**
     * The decision matrix of {@link Compressor#prepare}: the chosen {@code Content-Encoding}
     * (or none) for each combination of request {@code Accept-Encoding}, response content type,
     * content length against a threshold of 10, and the status/method/version pass-through rules.
     */
    @Test
    void prepareDecisionMatrix() throws Exception {
        Compressor compressor = new Compressor(defaultStrategy(10));
        ChannelHandlerContext ctx = context();
        Set<Compressor.Algorithm> available = EnumSet.of(Compressor.Algorithm.SNAPPY, Compressor.Algorithm.GZIP, Compressor.Algorithm.DEFLATE);
        if (compressor.determineEncoding(List.of("br").iterator()) == Compressor.Algorithm.BR) {
            available.add(Compressor.Algorithm.BR);
        }
        if (compressor.determineEncoding(List.of("zstd").iterator()) == Compressor.Algorithm.ZSTD) {
            available.add(Compressor.Algorithm.ZSTD);
        }
        List<String> failures = new ArrayList<>();
        for (String acceptEncoding : ACCEPT_ENCODINGS) {
            for (String contentType : CONTENT_TYPES) {
                for (long contentLength : CONTENT_LENGTHS) {
                    for (int variant = 0; variant < 5; variant++) {
                        HttpMethod method = variant == 1 ? HttpMethod.HEAD : HttpMethod.GET;
                        HttpResponseStatus status = variant == 2 ? HttpResponseStatus.NO_CONTENT : variant == 3 ? HttpResponseStatus.NOT_MODIFIED : HttpResponseStatus.OK;
                        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, "/");
                        if (acceptEncoding != null) {
                            request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, acceptEncoding);
                        }
                        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
                        if (contentType != null) {
                            response.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
                        }
                        if (variant == 4) {
                            response.headers().add(HttpHeaderNames.CONTENT_ENCODING, "custom");
                        }
                        boolean eligible = variant == 0
                            && contentType != null
                            && (contentLength == -1 || contentLength >= 10)
                            && MediaType.isTextBased(contentType);
                        Compressor.Algorithm expected = eligible && acceptEncoding != null
                            ? Compressor.determineEncoding(List.of(acceptEncoding).iterator(), available)
                            : null;
                        Compressor.Session session = compressor.prepare(ctx, request, response, contentLength);
                        String actual = response.headers().get(HttpHeaderNames.CONTENT_ENCODING);
                        String expectedHeader = variant == 4 ? "custom" : expected == null ? null : expected.contentEncoding.toString();
                        if ((session != null) != (expected != null) || !Objects.equals(expectedHeader, actual)) {
                            failures.add("ae=" + acceptEncoding + " ct=" + contentType + " len=" + contentLength + " variant=" + variant + " expected=" + expected + " actual=" + actual);
                        }
                        if (session != null) {
                            session.discard();
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
        // pin a few concrete outcomes
        assertEquals(Compressor.Algorithm.GZIP, Compressor.determineEncoding(List.of("gzip").iterator(), available));
        assertEquals(Compressor.Algorithm.DEFLATE, Compressor.determineEncoding(List.of("deflate").iterator(), available));
        assertNull(Compressor.determineEncoding(List.of("identity").iterator(), available));
    }

    /**
     * The content type is only inspected once the request accepts some compression and the
     * response is otherwise eligible.
     */
    @Test
    void contentTypeIsNotInspectedWithoutAcceptableEncoding() {
        int[] calls = {0};
        HttpCompressionStrategy counting = new HttpCompressionStrategy() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public boolean shouldCompress(HttpResponse response) {
                calls[0]++;
                return true;
            }

            @Override
            public int getMaxZstdEncodeSize() {
                return 1 << 20;
            }
        };
        Compressor compressor = new Compressor(counting);
        ChannelHandlerContext ctx = context();
        for (String acceptEncoding : Arrays.asList(null, "", "identity", "gzip;q=0", "x-unknown")) {
            HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            if (acceptEncoding != null) {
                request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, acceptEncoding);
            }
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            assertNull(compressor.prepare(ctx, request, response, 100));
        }
        assertEquals(0, calls[0]);
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        Compressor.Session session = compressor.prepare(ctx, request, response, 100);
        assertEquals(1, calls[0]);
        session.discard();
    }

    @Test
    void compressorIsSharedPerStrategy() throws Exception {
        DefaultHttpCompressionStrategy strategy = defaultStrategy(10);
        assertSame(Compressor.forStrategy(strategy), Compressor.forStrategy(strategy));
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
