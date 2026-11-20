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
package io.micronaut.web.router.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceCacheTest {

    @TempDir
    Path base;

    @Test
    void aFileIsCachedUntilItChanges() throws IOException {
        Path file = base.resolve("a.txt");
        Files.writeString(file, "one");
        URL url = file.toUri().toURL();
        StaticResourceCache cache = new StaticResourceCache(2, 10);

        StaticResourceCache.Entry first = cache.get(url);
        assertNotNull(first);
        assertArrayEquals("one".getBytes(), first.content());
        assertSame(first, cache.get(url));

        Files.writeString(file, "two");
        Files.setLastModifiedTime(file, FileTime.fromMillis(first.lastModified() + 5000));
        assertArrayEquals("two".getBytes(), cache.get(url).content());

        Files.writeString(file, "a larger one");
        assertNull(cache.get(url), "over the size of an entry");
        assertEquals(0, cache.size());
    }

    @Test
    void theNumberOfEntriesIsBounded() throws IOException {
        StaticResourceCache cache = new StaticResourceCache(2, 10);
        for (int i = 0; i < 5; i++) {
            Path file = base.resolve(i + ".txt");
            Files.writeString(file, "f" + i);
            assertNotNull(cache.get(file.toUri().toURL()));
            assertTrue(cache.size() <= 2);
        }
    }

    @Test
    void aClasspathResourceIsCached() {
        URL url = getClass().getClassLoader().getResource("io/micronaut/web/router/resource/StaticResourceCacheTest.class");
        StaticResourceCache cache = new StaticResourceCache(1, 1 << 20);
        StaticResourceCache.Entry entry = cache.get(url);
        assertNotNull(entry);
        assertSame(entry, cache.get(url));
    }

    @Test
    void theSizeIsPositive() {
        assertThrows(IllegalArgumentException.class, () -> new StaticResourceCache(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new StaticResourceCache(1, 0));
    }
}
