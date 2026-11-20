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
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.web.router.resource.PreCompressedResources.Coding.BR;
import static io.micronaut.web.router.resource.PreCompressedResources.Coding.GZIP;
import static io.micronaut.web.router.resource.PreCompressedResources.Coding.ZSTD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PreCompressedResourcesTest {

    @TempDir
    Path base;

    @Test
    void theAcceptableCodingsFollowTheWeights() {
        assertEquals(List.of(), acceptable());
        assertEquals(List.of(), acceptable(""));
        assertEquals(List.of(BR, ZSTD, GZIP), acceptable("*"));
        assertEquals(List.of(BR, GZIP), acceptable("gzip, br"));
        assertEquals(List.of(BR, GZIP), acceptable("gzip", "br"));
        assertEquals(List.of(GZIP, BR), acceptable("br;q=0.5, gzip"));
        assertEquals(List.of(GZIP), acceptable("br;q=0, gzip"));
        assertEquals(List.of(ZSTD, GZIP), acceptable("*;q=0.2, br;q=0, zstd"));
        assertEquals(List.of(GZIP), acceptable("x-gzip"));
        assertEquals(List.of(GZIP), acceptable("GZip ; Q=0.8"));
        assertEquals(List.of(), acceptable("identity"));
        assertEquals(List.of(), acceptable("deflate, snappy"));
        assertEquals(List.of(), acceptable("br;q=0.5, identity"));
        assertEquals(List.of(BR), acceptable("br, identity;q=0.5"));
        assertEquals(List.of(BR), acceptable("br;q=0.5, identity;q=0.5"));
        assertEquals(List.of(), acceptable("br;q=1.5"));
        assertEquals(List.of(), acceptable("br;q=abc"));
        assertEquals(List.of(GZIP), acceptable("br;q=, gzip"));
    }

    @Test
    void theFirstCodingWithASiblingIsFound() throws IOException {
        Files.writeString(base.resolve("app.js"), "js");
        Files.writeString(base.resolve("app.js.gz"), "gz");
        Files.writeString(base.resolve("app.js.zst"), "zst");
        ResourceLoader loader = new DefaultFileSystemResourceLoader(base);
        StaticResourceResolver.ResolvedResource original = StaticResourceResolver.resolveResource(List.of(loader), "app.js", null).orElseThrow();
        PreCompressedResources resources = new PreCompressedResources();

        PreCompressedResources.Sibling sibling = resources.find(original, List.of(BR, ZSTD, GZIP), r -> true);
        assertNotNull(sibling);
        assertEquals(ZSTD, sibling.coding());
        assertEquals(base.resolve("app.js.zst").toRealPath(), Path.of(sibling.url().getPath()));
        assertEquals(GZIP, resources.find(original, List.of(GZIP, ZSTD), r -> true).coding());
        assertNull(resources.find(original, List.of(BR), r -> true));
        assertNull(resources.find(original, List.of(), r -> true));
        // a sibling the predicate refuses is a miss
        assertEquals(GZIP, resources.find(original, List.of(ZSTD, GZIP), r -> !r.path().endsWith(".zst")).coding());
        // a file location is checked again
        Files.delete(base.resolve("app.js.zst"));
        assertEquals(GZIP, resources.find(original, List.of(ZSTD, GZIP), r -> true).coding());
        // a resource without its loader has no siblings
        assertNull(resources.find(new StaticResourceResolver.ResolvedResource(original.url(), false), List.of(GZIP), r -> true));
    }

    @Test
    void aClasspathLookupIsRemembered() {
        AtomicInteger lookups = new AtomicInteger();
        ClassPathResourceLoader classpath = ClassPathResourceLoader.defaultLoader(getClass().getClassLoader());
        ClassPathResourceLoader counting = new ClassPathResourceLoader() {
            @Override
            public ClassLoader getClassLoader() {
                return classpath.getClassLoader();
            }

            @Override
            public Optional<java.io.InputStream> getResourceAsStream(String path) {
                return classpath.getResourceAsStream(path);
            }

            @Override
            public Optional<URL> getResource(String path) {
                lookups.incrementAndGet();
                return classpath.getResource(path);
            }

            @Override
            public java.util.stream.Stream<URL> getResources(String name) {
                return classpath.getResources(name);
            }

            @Override
            public boolean supportsPrefix(String path) {
                return classpath.supportsPrefix(path);
            }

            @Override
            public ResourceLoader forBase(String basePath) {
                return this;
            }
        };
        // a resource of a jar: a jar does not change
        URL url = classpath.getResource("org/junit/jupiter/api/Test.class").orElseThrow();
        assertEquals("jar", url.getProtocol());
        StaticResourceResolver.ResolvedResource original = new StaticResourceResolver.ResolvedResource(url, false, counting,
            "org/junit/jupiter/api/Test.class");
        PreCompressedResources resources = new PreCompressedResources();
        for (int i = 0; i < 3; i++) {
            assertNull(resources.find(original, List.of(BR, GZIP), r -> true));
        }
        assertEquals(2, lookups.get());
    }

    @Test
    void aClasspathSiblingIsOfTheEntryOfTheResource() throws IOException {
        Path first = Files.createDirectories(base.resolve("first/pub"));
        Path second = Files.createDirectories(base.resolve("second/pub"));
        Files.writeString(first.resolve("app.js"), "js");
        Files.writeString(second.resolve("app.js.gz"), "another gz");
        Files.writeString(second.resolve("other.js"), "other");
        Files.writeString(second.resolve("other.js.gz"), "other gz");
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{
            base.resolve("first").toUri().toURL(), base.resolve("second").toUri().toURL()}, null)) {
            ResourceLoader loader = ClassPathResourceLoader.defaultLoader(classLoader).forBase("classpath:pub");
            PreCompressedResources resources = new PreCompressedResources();

            StaticResourceResolver.ResolvedResource app = StaticResourceResolver.resolveResource(List.of(loader), "app.js", null).orElseThrow();
            assertNull(resources.find(app, List.of(GZIP), r -> true), "the gz of another entry");

            StaticResourceResolver.ResolvedResource other = StaticResourceResolver.resolveResource(List.of(loader), "other.js", null).orElseThrow();
            assertEquals(GZIP, resources.find(other, List.of(GZIP), r -> true).coding());
            // an exploded classpath is resolved again: a removed sibling is a miss
            Files.delete(second.resolve("other.js.gz"));
            assertNull(resources.find(other, List.of(GZIP), r -> true));
        }
    }

    private static List<PreCompressedResources.Coding> acceptable(String... values) {
        return PreCompressedResources.acceptable(List.of(values));
    }
}
