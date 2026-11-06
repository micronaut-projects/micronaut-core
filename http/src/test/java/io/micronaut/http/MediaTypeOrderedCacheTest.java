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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the parsed media type list cache used by {@link MediaType#orderedOf(List)}.
 */
class MediaTypeOrderedCacheTest {

    private static final String BROWSER_ACCEPT =
        "text/plain,text/html;q=0.9,application/xhtml+xml;q=0.9,application/xml;q=0.8,*/*;q=0.7";

    @BeforeEach
    void clearCache() {
        MediaType.clearOrderedCache();
    }

    @Test
    void repeatedParsesOfTheSameHeaderAreEqual() {
        List<MediaType> first = MediaType.orderedOf(List.of(BROWSER_ACCEPT));
        List<MediaType> second = MediaType.orderedOf(List.of(BROWSER_ACCEPT));

        // the second call is served from the cache, which makes any comparison of the two lists
        // with each other trivially true: the content is asserted once instead
        assertSame(first, second);
        assertEquals(
            "text/plain;q=1;text/html;q=0.9;application/xhtml+xml;q=0.9;application/xml;q=0.8;*/*;q=0.7",
            describe(second)
        );
    }

    @Test
    void aCachedMediaTypeCannotBeCorruptedThroughItsParameters() {
        String header = "text/html;v=1,application/json";
        List<MediaType> first = MediaType.orderedOf(List.of(header));
        MediaType html = first.get(0);
        assertEquals("1", html.getVersion());

        // the parameters are exposed as a view: mutating it must be refused, not shared
        Collection<String> values = html.getParameters().values();
        assertThrows(UnsupportedOperationException.class, values::clear);
        Map<CharSequence, String> map = html.getParametersMap();
        assertThrows(UnsupportedOperationException.class, () -> map.remove("v"));

        List<MediaType> second = MediaType.orderedOf(List.of(header));
        assertEquals("1", second.get(0).getVersion());
        assertEquals("text/html;v=1", second.get(0).toString());
    }

    @Test
    void aCachedHeaderParsesToTheSameResultAsAnUncachedOne() {
        List<MediaType> cold = MediaType.orderedOf(List.of(BROWSER_ACCEPT));
        String description = describe(cold);
        MediaType.clearOrderedCache();
        List<MediaType> reparsed = MediaType.orderedOf(List.of(BROWSER_ACCEPT));

        assertNotSame(cold, reparsed);
        assertEquals(description, describe(reparsed));
        assertEquals(
            "text/plain;q=1;text/html;q=0.9;application/xhtml+xml;q=0.9;application/xml;q=0.8;*/*;q=0.7",
            description
        );
    }

    @Test
    void theReturnedListCannotBeMutatedByACaller() {
        List<MediaType> types = MediaType.orderedOf(List.of(BROWSER_ACCEPT));

        assertThrows(UnsupportedOperationException.class, () -> types.add(MediaType.APPLICATION_JSON_TYPE));
        assertThrows(UnsupportedOperationException.class, () -> types.set(0, MediaType.APPLICATION_JSON_TYPE));
        assertThrows(UnsupportedOperationException.class, () -> types.remove(0));
        assertThrows(UnsupportedOperationException.class, types::clear);

        assertEquals(describe(types), describe(MediaType.orderedOf(List.of(BROWSER_ACCEPT))));
    }

    @Test
    void aHeaderNamingASingleMediaTypeKeepsUsingTheFastPath() {
        List<MediaType> types = MediaType.orderedOf(List.of("application/json"));

        assertEquals(List.of(MediaType.APPLICATION_JSON_TYPE), types);
        assertThrows(UnsupportedOperationException.class, () -> types.add(MediaType.TEXT_PLAIN_TYPE));
        // there is nothing to split, parse and sort, so the value does not take up a cache slot
        assertEquals(0, MediaType.orderedCacheSize());
    }

    @Test
    void theCacheIsBoundedAndKeepsParsingCorrectlyWhenFull() {
        var expected = new ArrayList<String>();
        for (int i = 0; i < 5_000; i++) {
            String header = "application/vnd.example" + i + "+json,*/*;q=0.5";
            expected.add(describe(MediaType.orderedOf(List.of(header))));
        }

        assertTrue(MediaType.orderedCacheSize() <= 256,
            "cache grew to " + MediaType.orderedCacheSize() + " entries");

        // every header still parses, whether or not it made it into the cache
        for (int i = 0; i < 5_000; i++) {
            String header = "application/vnd.example" + i + "+json,*/*;q=0.5";
            assertEquals("application/vnd.example" + i + "+json;q=1;*/*;q=0.5", expected.get(i));
            assertEquals(expected.get(i), describe(MediaType.orderedOf(List.of(header))));
        }
    }

    @Test
    void theCacheRemainsBoundedUnderConcurrentMisses() throws Exception {
        int threads = 64;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int attempt = 0; attempt < 25; attempt++) {
                MediaType.clearOrderedCache();
                for (int i = 0; i < 255; i++) {
                    MediaType.orderedOf(List.of("application/x-prefill-" + i + ",*/*"));
                }

                CyclicBarrier barrier = new CyclicBarrier(threads);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    String header = "application/x-concurrent-" + attempt + '-' + i + ",text/plain";
                    futures.add(executor.submit(() -> {
                        barrier.await();
                        MediaType.orderedOf(List.of(header));
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get(10, TimeUnit.SECONDS);
                }
                if (MediaType.orderedCacheSize() > 256) {
                    break;
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(MediaType.orderedCacheSize() <= 256,
            "cache grew to " + MediaType.orderedCacheSize() + " entries");
    }

    @Test
    void aCommonHeaderCanStillBeCachedAfterDistinctUntrustedValues() {
        for (int i = 0; i < 256; i++) {
            MediaType.orderedOf(List.of("application/x-untrusted-" + i + ",*/*"));
        }

        List<MediaType> first = MediaType.orderedOf(List.of(BROWSER_ACCEPT));
        List<MediaType> second = MediaType.orderedOf(List.of(BROWSER_ACCEPT));

        assertSame(first, second);
    }

    @Test
    void aRecurringHeaderKeepsItsSlotWhileOneOffValuesComeAndGo() {
        List<MediaType> cached = MediaType.orderedOf(List.of(BROWSER_ACCEPT));
        // read it again so that it stops being a first seen value on probation
        assertSame(cached, MediaType.orderedOf(List.of(BROWSER_ACCEPT)));

        for (int i = 0; i < 5_000; i++) {
            MediaType.orderedOf(List.of("application/x-one-off-" + i + ",*/*"));
            assertSame(cached, MediaType.orderedOf(List.of(BROWSER_ACCEPT)),
                "the recurring header lost its slot after " + i + " one-off values");
        }
    }

    @Test
    void anOversizedHeaderBypassesTheCacheButStillParses() {
        var header = new StringBuilder("text/plain;q=0.4");
        for (int i = 0; header.length() <= 256; i++) {
            header.append(",application/vnd.example").append(i).append("+json;q=0.9");
        }
        String oversized = header.toString();

        List<MediaType> types = MediaType.orderedOf(List.of(oversized));

        assertEquals(0, MediaType.orderedCacheSize());
        assertEquals("text/plain", types.get(types.size() - 1).getName());
        assertEquals("0.9", types.get(0).getQuality());
        assertEquals(describe(types), describe(MediaType.orderedOf(List.of(oversized))));
    }

    @Test
    void unusualHeadersStillParseAsBefore() {
        assertEquals(List.of(), MediaType.orderedOf(List.of("")));
        assertEquals(List.of(), MediaType.orderedOf(List.of(",,,")));
        assertEquals(List.of(), MediaType.orderedOf(List.of("not-a-media-type")));
        assertEquals(
            "application/json;q=1",
            describe(MediaType.orderedOf(List.of("not-a-media-type,application/json")))
        );
        assertEquals(
            "text/html;q=1;*/*;q=1",
            describe(MediaType.orderedOf(List.of("  text/html ,  */* ")))
        );
        // repeating the same values from the cache does not change the answer
        assertEquals(List.of(), MediaType.orderedOf(List.of(",,,")));
        assertEquals(
            "text/html;q=1;*/*;q=1",
            describe(MediaType.orderedOf(List.of("  text/html ,  */* ")))
        );
    }

    @Test
    void multipleHeaderLinesAreStillCombinedAndOrdered() {
        List<MediaType> types = MediaType.orderedOf(List.of("text/html;q=0.5", "application/json"));

        assertEquals("application/json;q=1;text/html;q=0.5", describe(types));
        assertThrows(UnsupportedOperationException.class, () -> types.add(MediaType.TEXT_PLAIN_TYPE));
    }

    private static String describe(List<MediaType> mediaTypes) {
        return mediaTypes.stream()
            .map(mt -> mt.getName() + ";q=" + mt.getQuality())
            .collect(Collectors.joining(";"));
    }
}
