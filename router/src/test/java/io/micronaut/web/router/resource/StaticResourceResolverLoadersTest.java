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

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StaticResourceResolver#resolve(List, String, String)}: the resolution of a path relative
 * to the base of resource loaders, shared by the configured static resources and the static
 * resources of handler routes.
 */
class StaticResourceResolverLoadersTest {

    @TempDir
    Path first;

    @TempDir
    Path second;

    @Test
    void anEmptyPathIsTheIndexPage() throws IOException {
        write(first.resolve("index.html"));
        List<ResourceLoader> loaders = List.of(new DefaultFileSystemResourceLoader(first));

        assertEquals(first.resolve("index.html").toRealPath(), path(StaticResourceResolver.resolve(loaders, "", "index.html")));
        assertEquals(first.resolve("index.html").toRealPath(), path(StaticResourceResolver.resolve(loaders, "/", "index.html")));
        assertTrue(StaticResourceResolver.resolve(loaders, "", null).isEmpty(), "no index page");
    }

    @Test
    void aDirectoryIsItsIndexPageOrNothing() throws IOException {
        write(first.resolve("docs/index.html"));
        Files.createDirectories(first.resolve("empty"));
        List<ResourceLoader> loaders = List.of(new DefaultFileSystemResourceLoader(first));

        assertEquals(first.resolve("docs/index.html").toRealPath(), path(StaticResourceResolver.resolve(loaders, "docs", "index.html")));
        assertEquals(first.resolve("docs/index.html").toRealPath(), path(StaticResourceResolver.resolve(loaders, "docs/", "index.html")));
        assertTrue(StaticResourceResolver.resolve(loaders, "docs", null).isEmpty(), "no index page");
        assertTrue(StaticResourceResolver.resolve(loaders, "empty", "index.html").isEmpty(), "a directory without an index page");
        assertTrue(StaticResourceResolver.resolve(loaders, "missing.txt", "index.html").isEmpty());
    }

    @Test
    void eachLoaderIsTriedWithThePathBeforeTheIndexPageUnderIt() throws IOException {
        // a file without an extension that only the second loader has
        write(second.resolve("LICENSE"));
        write(second.resolve("guide/index.html"));
        List<ResourceLoader> loaders = List.of(new DefaultFileSystemResourceLoader(first), new DefaultFileSystemResourceLoader(second));

        assertEquals(second.resolve("LICENSE").toRealPath(), path(StaticResourceResolver.resolve(loaders, "LICENSE", "index.html")));
        assertEquals(second.resolve("guide/index.html").toRealPath(), path(StaticResourceResolver.resolve(loaders, "guide", "index.html")));
    }

    private static Path path(Optional<URL> url) {
        assertTrue(url.isPresent());
        try {
            return Path.of(url.get().toURI()).toRealPath();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, file.getFileName().toString());
    }
}
