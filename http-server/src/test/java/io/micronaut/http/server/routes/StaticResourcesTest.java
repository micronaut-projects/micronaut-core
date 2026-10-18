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
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            assertTrue(DefaultStaticResources.isRelative(path), path);
        }
    }

    @Test
    void aPathThatLeavesTheBaseIsNotRelative() {
        for (String path : new String[]{
            "..", ".", "../secret", "a/../../secret", "a/./b", "a/..", "a/.",
            "/etc/passwd", "a\\..\\b", "..\\secret", "C:secret", "c:/secret", "a\0.txt",
            "%2e%2e/secret", "%2E%2E/secret", "..%2fsecret", "..%2Fsecret", "..%5csecret", "a%00.txt"
        }) {
            assertFalse(DefaultStaticResources.isRelative(path), path);
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
        assertSame(DefaultStaticResources.class, configured.getClass());
    }

    @Test
    void theOptionsAreChecked() {
        StaticResources resources = StaticResources.classpath("public");
        for (String name : new String[]{"", ".", "..", "a/index.html", "a\\index.html", "c:index.html"}) {
            assertThrows(IllegalArgumentException.class, () -> resources.indexFile(name), name);
        }
        for (String name : new String[]{"", "a-b", "a.b", "a/b"}) {
            assertThrows(IllegalArgumentException.class, () -> resources.pathVariable(name), name);
        }
        assertEquals("default.htm", resources.indexFile("default.htm").indexFile());
        assertThrows(IllegalArgumentException.class, () -> StaticResources.fileSystem("/no/such/directory/of/static/resources"));
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of("string:hello"));
    }

    @Test
    void anotherSpellingOfAPathIsAnAlias() {
        assertTrue(DefaultStaticResources.isAlias("css//site.css", "/assets/css//site.css"));
        assertTrue(DefaultStaticResources.isAlias("css/site.css", "/assets/css%2fsite.css"));
        assertTrue(DefaultStaticResources.isAlias("css/site.css", "/assets/css%2Fsite.css"));
        // a backslash is not relative
        assertFalse(DefaultStaticResources.isRelative("css\\site.css"));
        // an encoded slash of the prefix is not an alias of the path of the resource
        assertFalse(DefaultStaticResources.isAlias("css/site.css", "/a%2Fb/css/site.css"));
        assertFalse(DefaultStaticResources.isAlias("hello.txt", "/tenant/acme%2Feu/assets/hello.txt"));
        assertFalse(DefaultStaticResources.isAlias("", "/a%2Fb"));
        assertTrue(DefaultStaticResources.isAlias("a/b/c.txt", "/a%2Fb/c.txt"));
        assertFalse(DefaultStaticResources.isAlias("css/site.css", "/assets/css/site.css"));
        assertFalse(DefaultStaticResources.isAlias("my file.txt", "/assets/my%20file.txt"));
    }

    @Test
    void aFileIsSpelledAsRequested(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve("docs"));
        Files.writeString(base.resolve("docs/index.html"), "index");
        Files.writeString(base.resolve("hello.txt"), "hello");
        File hello = base.resolve("hello.txt").toFile();

        assertTrue(DefaultStaticResources.isSpelledAsRequested(hello, "hello.txt"));
        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("docs/index.html").toFile(), "docs"));
        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("docs/index.html").toFile(), "docs/"));
        File upper = base.resolve("HELLO.TXT").toFile();
        if (upper.exists()) {
            // a file system that ignores the case
            assertFalse(DefaultStaticResources.isSpelledAsRequested(upper, "HELLO.TXT"));
            assertFalse(DefaultStaticResources.isSpelledAsRequested(base.resolve("DOCS/index.html").toFile(), "DOCS"));
        }
    }

    @Test
    void aLocationNeedsASupportedPrefix() {
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of("public"));
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of(new String[0]));
        assertEquals(StaticResources.DEFAULT_PATH_VARIABLE, StaticResources.of("classpath:public", "file:/tmp").pathVariable());
    }
}
