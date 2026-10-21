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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.bind.MatchedPathVariables;
import io.micronaut.http.server.types.files.SystemFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        // the router ignores one trailing slash, not more
        assertTrue(DefaultStaticResources.isAlias("hello.txt//", "/files/hello.txt///"));
        assertTrue(DefaultStaticResources.isAlias("hello.txt/", "/files/hello.txt//"));
        assertFalse(DefaultStaticResources.isAlias("docs", "/files/docs/"));
        assertFalse(DefaultStaticResources.isAlias("docs", "/encoded%2Fprefix/docs/"));
        assertFalse(DefaultStaticResources.isAlias("my file.txt", "/assets/my%20file.txt/"));
        // the raw path is not decoded: any charset of the router
        assertFalse(DefaultStaticResources.isAlias("caf\u00e9.txt", "/assets/caf%E9.txt"));
        assertFalse(DefaultStaticResources.isAlias("caf\u00e9.txt", "/assets/caf%C3%A9.txt"));
        assertFalse(DefaultStaticResources.isAlias("css/site.css", "/assets/css/site.css"));
        assertFalse(DefaultStaticResources.isAlias("my file.txt", "/assets/my%20file.txt"));
    }

    @Test
    void aFileIsSpelledAsRequested(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve("docs"));
        Files.writeString(base.resolve("docs/index.html"), "index");
        Files.writeString(base.resolve("hello.txt"), "hello");
        File hello = base.resolve("hello.txt").toFile();

        assertTrue(DefaultStaticResources.isSpelledAsRequested(hello, "hello.txt", false));
        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("docs/index.html").toFile(), "docs", true));
        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("docs/index.html").toFile(), "docs/", true));
        File upper = base.resolve("HELLO.TXT").toFile();
        if (upper.exists()) {
            // a file system that ignores the case
            assertFalse(DefaultStaticResources.isSpelledAsRequested(upper, "HELLO.TXT", false));
            assertFalse(DefaultStaticResources.isSpelledAsRequested(base.resolve("DOCS/index.html").toFile(), "DOCS", true));
        }
    }

    @Test
    void anIndexFileNamedLikeItsDirectoryDoesNotSkipTheSpellingOfTheDirectories(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve("secret/home"));
        Files.writeString(base.resolve("secret/home/home"), "index");

        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("secret/home/home").toFile(), "secret/home", true));
        File upper = base.resolve("SECRET/home/home").toFile();
        if (upper.exists()) {
            // a file system that ignores the case: the index file home of SECRET/home
            assertFalse(DefaultStaticResources.isSpelledAsRequested(upper, "SECRET/home", true));
        }
    }

    @Test
    void anEmptyOrSeveralSegmentsVariableOfThePrefixIsNotFound(@TempDir Path base) throws Exception {
        Files.writeString(base.resolve("hello.txt"), "hello");
        StaticResources resources = StaticResources.fileSystem(base);
        assertEquals(HttpStatus.OK, resources.handle(HttpRequest.GET("/docs/v1/hello.txt"), variables(Map.of("v", "v1", "path", "hello.txt"))).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, resources.handle(HttpRequest.GET("/docs//hello.txt"), variables(Map.of("v", "", "path", "hello.txt"))).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, resources.handle(HttpRequest.GET("/docs/a/b/hello.txt"), variables(Map.of("v", "a/b", "path", "hello.txt"))).getStatus());
    }

    @Test
    void anotherSpellingOfALocationIsAMissAndTheNextLocationServesTheExactName(@TempDir Path base) throws Exception {
        Path first = Files.createDirectories(base.resolve("first"));
        Path second = Files.createDirectories(base.resolve("second"));
        Files.writeString(first.resolve("HELLO.TXT"), "upper");
        Files.writeString(second.resolve("hello.txt"), "lower");
        StaticResources resources = StaticResources.of("file:" + first, "file:" + second);

        HttpResponse<?> response = resources.handle(HttpRequest.GET("/assets/hello.txt"), variables(Map.of("path", "hello.txt")));
        assertEquals(HttpStatus.OK, response.getStatus());
        SystemFile file = assertInstanceOf(SystemFile.class, response.body());
        assertEquals(second.resolve("hello.txt").toRealPath(), file.getFile().toPath().toRealPath());
    }

    @Test
    void aNameTheFileSystemDoesNotAllowIsNotFound() throws Exception {
        StaticResources resources = StaticResources.of(new FixedLoader() {
            @Override
            public Optional<URL> getResource(String path) {
                throw new InvalidPathException(path, "Illegal char <?>");
            }
        });
        assertEquals(HttpStatus.NOT_FOUND, resources.handle(HttpRequest.GET("/files/a%3Fb.txt"), variables(Map.of("path", "a?b.txt"))).getStatus());
    }

    private static PathVariables variables(Map<String, Object> values) {
        return new MatchedPathVariables(values, ConversionService.SHARED);
    }

    /**
     * A loader without resources.
     */
    private static class FixedLoader implements ResourceLoader {
        @Override
        public Optional<InputStream> getResourceAsStream(String path) {
            return Optional.empty();
        }

        @Override
        public Optional<URL> getResource(String path) {
            return Optional.empty();
        }

        @Override
        public Stream<URL> getResources(String name) {
            return Stream.empty();
        }

        @Override
        public boolean supportsPrefix(String path) {
            return false;
        }

        @Override
        public ResourceLoader forBase(String basePath) {
            return this;
        }
    }

    @Test
    void anotherUnicodeSpellingOrCaseOfANameIsNotListed(@TempDir Path base) throws IOException {
        Files.createDirectories(base.resolve("home"));
        Files.writeString(base.resolve("home/HOME"), "index");
        Files.writeString(base.resolve("\u304c.txt"), "ga");
        Files.writeString(base.resolve("\uD801\uDC28.txt"), "deseret");

        // the index file HOME of home, requested as HOME
        File index = base.resolve("HOME/HOME").toFile();
        if (index.exists()) {
            assertFalse(DefaultStaticResources.isSpelledAsRequested(index, "HOME", true));
        }
        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("home/HOME").toFile(), "home", true));
        // a decomposed spelling and an uppercase supplementary letter
        for (String name : new String[]{"\u304b\u3099.txt", "\uD801\uDC00.txt"}) {
            File other = base.resolve(name).toFile();
            if (other.exists()) {
                assertFalse(DefaultStaticResources.isSpelledAsRequested(other, name, false), name);
            }
        }
        assertTrue(DefaultStaticResources.isSpelledAsRequested(base.resolve("\u304c.txt").toFile(), "\u304c.txt", false));
    }

    @Test
    void aLocationNeedsASupportedPrefix() {
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of("public"));
        assertThrows(IllegalArgumentException.class, () -> StaticResources.of(new String[0]));
        assertEquals(StaticResources.DEFAULT_PATH_VARIABLE, StaticResources.of("classpath:public", "file:/tmp").pathVariable());
    }
}
