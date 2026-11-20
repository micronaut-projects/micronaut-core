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
package io.micronaut.http.server.types.files;

import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.resource.PreCompressedResources;
import io.micronaut.web.router.resource.StaticResourceCache;
import io.micronaut.web.router.resource.StaticResourceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceResponsesTest {

    @TempDir
    Path base;

    @Test
    void aResourceIsAFileWithoutTheOptions() throws IOException {
        StaticResourceResolver.ResolvedResource app = fileResource("app.js", "js");
        MutableHttpResponse<?> response = StaticResourceResponses.respond(HttpRequest.GET("/app.js"), app, null, null, r -> true);
        assertInstanceOf(SystemFile.class, response.body());
        assertNull(response.getHeaders().get(HttpHeaders.VARY));
        assertNull(response.getHeaders().get(HttpHeaders.ETAG));
    }

    @Test
    void aSiblingOfTheFileSystemHasTheHeadersOfItsEncoding() throws IOException {
        StaticResourceResolver.ResolvedResource app = fileResource("app.js", "js");
        Files.writeString(base.resolve("app.js.gz"), "gzipped");
        PreCompressedResources preCompressed = new PreCompressedResources();

        MutableHttpResponse<?> gzip = StaticResourceResponses.respond(HttpRequest.GET("/app.js").header(HttpHeaders.ACCEPT_ENCODING, "gzip"),
            app, preCompressed, null, StaticResourceResponses::isReadable);
        SystemFile file = assertInstanceOf(SystemFile.class, gzip.body());
        assertEquals("app.js.gz", file.getFile().getName());
        assertTrue(file.getMediaType().getName().contains("javascript"), "the media type of the resource");
        assertEquals("gzip", gzip.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        assertEquals(HttpHeaders.ACCEPT_ENCODING, gzip.getHeaders().get(HttpHeaders.VARY));
        String gzipTag = gzip.getHeaders().get(HttpHeaders.ETAG);
        assertTrue(gzipTag.endsWith("-gzip\""), gzipTag);

        MutableHttpResponse<?> identity = StaticResourceResponses.respond(HttpRequest.GET("/app.js"), app, preCompressed, null, StaticResourceResponses::isReadable);
        assertNull(identity.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        assertEquals(HttpHeaders.ACCEPT_ENCODING, identity.getHeaders().get(HttpHeaders.VARY));
        assertNotEquals(gzipTag, identity.getHeaders().get(HttpHeaders.ETAG));

        // a range is a range of the resource
        MutableHttpResponse<?> ranged = StaticResourceResponses.respond(HttpRequest.GET("/app.js")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip").header(HttpHeaders.RANGE, "bytes=0-1"), app, preCompressed, null, StaticResourceResponses::isReadable);
        assertNull(ranged.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        assertEquals("app.js", assertInstanceOf(SystemFile.class, ranged.body()).getFile().getName());
    }

    @Test
    void aCachedResourceIsServedFromMemoryUnlessRanged() throws IOException {
        StaticResourceResolver.ResolvedResource app = fileResource("app.js", "js");
        Files.writeString(base.resolve("app.js.gz"), "gzipped");
        StaticResourceCache cache = new StaticResourceCache(4, 1024);

        CachedFile cached = assertInstanceOf(CachedFile.class,
            StaticResourceResponses.respond(HttpRequest.GET("/app.js"), app, null, cache, r -> true).body());
        assertArrayEquals("js".getBytes(StandardCharsets.UTF_8), cached.content());
        assertEquals(2, cached.getLength());
        assertTrue(cached.getMediaType().getName().contains("javascript"));
        assertEquals(Files.getLastModifiedTime(base.resolve("app.js")).toMillis(), cached.getLastModified());

        CachedFile sibling = assertInstanceOf(CachedFile.class, StaticResourceResponses.respond(
            HttpRequest.GET("/app.js").header(HttpHeaders.ACCEPT_ENCODING, "gzip"), app, new PreCompressedResources(), cache, r -> true).body());
        assertArrayEquals("gzipped".getBytes(StandardCharsets.UTF_8), sibling.content());
        assertTrue(sibling.getMediaType().getName().contains("javascript"), "the media type of the resource");

        assertInstanceOf(SystemFile.class, StaticResourceResponses.respond(HttpRequest.GET("/app.js").header(HttpHeaders.RANGE, "bytes=0-0"),
            app, null, cache, r -> true).body());
    }

    @Test
    void aSiblingOfAJarIsStreamed() throws IOException {
        Path jar = base.resolve("assets.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            write(out, "pub/app.js", "js");
            write(out, "pub/app.js.br", "brotli");
        }
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            ResourceLoader loader = ClassPathResourceLoader.defaultLoader(classLoader).forBase("classpath:pub");
            StaticResourceResolver.ResolvedResource app = StaticResourceResolver.resolveResource(List.of(loader), "app.js", null).orElseThrow();
            assertTrue(StaticResourceResponses.isReadable(app));

            MutableHttpResponse<?> br = StaticResourceResponses.respond(HttpRequest.GET("/app.js").header(HttpHeaders.ACCEPT_ENCODING, "br"),
                app, new PreCompressedResources(), null, StaticResourceResponses::isReadable);
            StreamedFile streamed = assertInstanceOf(StreamedFile.class, br.body());
            try (InputStream stream = streamed.getInputStream()) {
                assertEquals("brotli", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertTrue(streamed.getMediaType().getName().contains("javascript"));
            assertEquals("br", br.getHeaders().get(HttpHeaders.CONTENT_ENCODING));

            StreamedFile original = assertInstanceOf(StreamedFile.class, StaticResourceResponses.body(app.url()));
            original.getInputStream().close();
        }
    }

    @Test
    void aDirectoryIsNotReadable() throws IOException {
        Files.createDirectories(base.resolve("dir"));
        assertFalse(StaticResourceResponses.isReadable(new StaticResourceResolver.ResolvedResource(base.resolve("dir").toUri().toURL(), false)));
    }

    private StaticResourceResolver.ResolvedResource fileResource(String name, String content) throws IOException {
        Files.writeString(base.resolve(name), content);
        return StaticResourceResolver.resolveResource(List.of(new DefaultFileSystemResourceLoader(base)), name, null).orElseThrow();
    }

    private static void write(JarOutputStream out, String name, String content) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
