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
package io.micronaut.http.server.routes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The options of {@link StaticResources} and the paths it serves.
 */
class StaticResourcesTest {

    @Test
    void aPathUnderTheBaseIsRelative() {
        for (String path : new String[]{"", "hello.txt", "css/site.css", "a/b/c.d.e", ".well-known/x", "a..b", "..a", "a/...", "a%20b.txt", "docs/"}) {
            assertTrue(StaticResources.isRelative(path), path);
        }
    }

    @Test
    void aPathThatLeavesTheBaseIsNotRelative() {
        for (String path : new String[]{
            "..", ".", "../secret", "a/../../secret", "a/./b", "a/..", "a/.",
            "/etc/passwd", "a\\..\\b", "..\\secret", "C:secret", "c:/secret", "a\0.txt",
            "%2e%2e/secret", "%2E%2E/secret", "..%2fsecret", "..%2Fsecret", "..%5csecret", "a%00.txt"
        }) {
            assertFalse(StaticResources.isRelative(path), path);
        }
    }

    @Test
    void theOptionsAreCopies() {
        StaticResources resources = StaticResources.classpath("public");
        assertEquals(StaticResources.DEFAULT_PATH_VARIABLE, resources.pathVariable());
        assertEquals(StaticResources.DEFAULT_INDEX_FILE, resources.indexFile());
        assertNull(resources.cacheControl());

        StaticResources configured = resources.pathVariable("file").indexFile(null).cacheControl("no-cache");
        assertEquals("file", configured.pathVariable());
        assertNull(configured.indexFile());
        assertEquals("no-cache", configured.cacheControl());
        // unchanged
        assertEquals(StaticResources.DEFAULT_PATH_VARIABLE, resources.pathVariable());
        assertEquals(StaticResources.DEFAULT_INDEX_FILE, resources.indexFile());
        assertNull(resources.cacheControl());
        assertSame(StaticResources.class, configured.getClass());
    }

    @Test
    void aLocationNeedsASupportedPrefix() {
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of("public"));
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of(new String[0]));
        assertEquals(StaticResources.DEFAULT_PATH_VARIABLE, StaticResources.of("classpath:public", "file:/tmp").pathVariable());
    }
}
